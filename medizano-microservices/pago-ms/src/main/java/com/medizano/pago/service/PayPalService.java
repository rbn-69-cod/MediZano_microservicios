package com.medizano.pago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.PayPalProperties;
import com.medizano.pago.dto.*;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.repository.PagoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.medizano.pago.exception.PaymentGatewayAuthenticationException;
import com.medizano.pago.exception.PaymentGatewayException;
import com.medizano.pago.exception.PaymentGatewayNotConfiguredException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PayPalService {

    private final PagoRepository pagoRepository;
    private final OrdenClient ordenClient;
    private final PayPalProperties payPalProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private String cachedToken = null;
    private Instant tokenExpiry = Instant.MIN;

    public PayPalService(PagoRepository pagoRepository,
                         OrdenClient ordenClient,
                         PayPalProperties payPalProperties,
                         ObjectMapper objectMapper) {
        this.pagoRepository = pagoRepository;
        this.ordenClient = ordenClient;
        this.payPalProperties = payPalProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public PayPalConfigResponse obtenerConfiguracionPublica() {
        return PayPalConfigResponse.builder()
                .clientId(payPalProperties.getClientId())
                .currency(payPalProperties.getCurrency())
                .baseUrl(payPalProperties.getBaseUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PagoDTO> listarPorOrden(Long ordenId) {
        return pagoRepository.findByOrdenIdOrderByCreatedAtDesc(ordenId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagoDTO obtenerPorId(Long id) {
        Pago p = pagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el registro de pago con ID: " + id));
        return mapToDTO(p);
    }

    public boolean isConfigured() {
        String clientId = payPalProperties.getClientId();
        String clientSecret = payPalProperties.getClientSecret();
        boolean idValid = clientId != null && !clientId.trim().isEmpty()
                && !clientId.toLowerCase().contains("placeholder")
                && !clientId.toLowerCase().contains("your_")
                && !clientId.toLowerCase().startsWith("tu_");
        boolean secretValid = clientSecret != null && !clientSecret.trim().isEmpty()
                && !clientSecret.toLowerCase().contains("placeholder")
                && !clientSecret.toLowerCase().contains("your_")
                && !clientSecret.toLowerCase().startsWith("tu_");
        return idValid && secretValid;
    }

    /**
     * Obtains OAuth 2.0 Access Token from PayPal Sandbox
     */
    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }

        String clientId = payPalProperties.getClientId();
        String clientSecret = payPalProperties.getClientSecret();
        boolean idPresent = clientId != null && !clientId.trim().isEmpty()
                && !clientId.toLowerCase().contains("placeholder")
                && !clientId.toLowerCase().contains("your_");
        boolean secretPresent = clientSecret != null && !clientSecret.trim().isEmpty()
                && !clientSecret.toLowerCase().contains("placeholder")
                && !clientSecret.toLowerCase().contains("your_");

        log.info("Verificación PayPal Sandbox: CLIENT_ID={}, CLIENT_SECRET={}",
                idPresent ? "PRESENT" : "MISSING",
                secretPresent ? "PRESENT" : "MISSING");

        if (!idPresent || !secretPresent) {
            throw new PaymentGatewayNotConfiguredException("PAYPAL", "PayPal Sandbox no está configurado.");
        }

        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (clientId.trim() + ":" + clientSecret.trim()).getBytes(StandardCharsets.UTF_8));

        String tokenUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v1/oauth2/token";
        log.info("Solicitando OAuth 2.0 Access Token a PayPal Sandbox: {}", tokenUrl);

        try {
            String responseBody = restClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(responseBody);
            this.cachedToken = node.get("access_token").asText();
            int expiresIn = node.has("expires_in") ? node.get("expires_in").asInt() : 3600;
            this.tokenExpiry = Instant.now().plusSeconds(expiresIn);

            log.info("OAuth 2.0 Access Token de PayPal Sandbox obtenido exitosamente (expira en {} s)", expiresIn);
            return this.cachedToken;

        } catch (RestClientResponseException ex) {
            log.error("Error al obtener token de PayPal Sandbox: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new PaymentGatewayAuthenticationException("PAYPAL", "Credenciales de PayPal Sandbox inválidas o no autorizadas.");
            }
            throw new PaymentGatewayException("Error al comunicarse con PayPal Sandbox: HTTP " + ex.getStatusCode().value(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al autenticar con PayPal Sandbox: {}", ex.getMessage(), ex);
            throw new PaymentGatewayException("Error inesperado al conectar con PayPal Sandbox: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        }
    }

    /**
     * Creates a new PayPal Order using Orders API v2 (POST /v2/checkout/orders)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public PayPalOrderResponse crearOrden(PayPalOrderRequest request) {
        // Validar orden interna con orden-ms primero
        OrdenClient.OrdenResponse orden = ordenClient.obtenerOrdenPorId(request.getOrdenId());
        if (orden == null) {
            throw new IllegalArgumentException("No se encontró la orden con ID: " + request.getOrdenId());
        }

        if ("PAGADA".equalsIgnoreCase(orden.getEstado())) {
            throw new IllegalStateException("Esta orden ya ha sido pagada previamente.");
        }

        String token = getAccessToken();


        // Calcular conversión PEN a USD
        BigDecimal amountPen = orden.getTotal();
        BigDecimal exchangeRate = payPalProperties.getExchangeRate() != null && payPalProperties.getExchangeRate().compareTo(BigDecimal.ZERO) > 0
                ? payPalProperties.getExchangeRate()
                : new BigDecimal("3.75");
        BigDecimal amountUsd = amountPen.divide(exchangeRate, 2, RoundingMode.HALF_UP);

        // Construir payload oficial PayPal Orders API v2
        Map<String, Object> orderPayload = new HashMap<>();
        orderPayload.put("intent", "CAPTURE");

        List<Map<String, Object>> purchaseUnits = new ArrayList<>();
        Map<String, Object> unit = new HashMap<>();
        unit.put("reference_id", "ORDEN-" + orden.getId());
        unit.put("description", "MediZano POS - " + orden.getNumeroOrden());
        unit.put("custom_id", String.valueOf(orden.getId()));

        Map<String, Object> amount = new HashMap<>();
        amount.put("currency_code", payPalProperties.getCurrency());
        amount.put("value", String.format(Locale.US, "%.2f", amountUsd));
        unit.put("amount", amount);
        purchaseUnits.add(unit);
        orderPayload.put("purchase_units", purchaseUnits);

        String ordersUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v2/checkout/orders";
        String requestId = UUID.randomUUID().toString();

        log.info("Creando orden en PayPal Sandbox API v2: {} (Monto USD: {})", ordersUrl, amountUsd);

        try {
            String responseBody = restClient.post()
                    .uri(ordersUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("PayPal-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(orderPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String paypalOrderId = root.get("id").asText();
            String status = root.get("status").asText();

            String approveUrl = "";
            if (root.has("links")) {
                for (JsonNode link : root.get("links")) {
                    if ("approve".equalsIgnoreCase(link.get("rel").asText())) {
                        approveUrl = link.get("href").asText();
                        break;
                    }
                }
            }

            // Registrar/Actualizar Pago en estado PENDING
            Pago pago = pagoRepository.findByOrdenIdAndStatus(orden.getId(), Pago.EstadoPago.PENDING)
                    .orElse(Pago.builder()
                            .ordenId(orden.getId())
                            .numeroOrden(orden.getNumeroOrden())
                            .provider("PAYPAL")
                            .currency(payPalProperties.getCurrency())
                            .build());

            pago.setAmount(amountUsd);
            pago.setPaypalOrderId(paypalOrderId);
            pago.setStatus(Pago.EstadoPago.PENDING);
            pago.setExternalStatus(status);
            pagoRepository.save(pago);

            log.info("Orden PayPal creada exitosamente: paypalOrderId={}, approveUrl={}", paypalOrderId, approveUrl);

            return PayPalOrderResponse.builder()
                    .ordenId(orden.getId())
                    .numeroOrden(orden.getNumeroOrden())
                    .paypalOrderId(paypalOrderId)
                    .status(status)
                    .approveUrl(approveUrl)
                    .amountPen(amountPen)
                    .amountUsd(amountUsd)
                    .currency(payPalProperties.getCurrency())
                    .clientId(payPalProperties.getClientId())
                    .build();

        } catch (RestClientResponseException ex) {
            log.error("Error al crear orden en PayPal Sandbox: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new PaymentGatewayAuthenticationException("PAYPAL", "Credenciales de PayPal Sandbox inválidas o no autorizadas.");
            }
            throw new PaymentGatewayException("Error al comunicarse con PayPal Sandbox para crear la orden: HTTP " + ex.getStatusCode().value(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al crear orden PayPal: {}", ex.getMessage(), ex);
            throw new PaymentGatewayException("Error inesperado al crear la orden en PayPal: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        }
    }

    /**
     * Captures payment for an authorized PayPal Order (POST /v2/checkout/orders/{id}/capture)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public PayPalCaptureResponse capturarOrden(String paypalOrderId) {
        Pago pago = pagoRepository.findByPaypalOrderId(paypalOrderId.trim())
                .orElseThrow(() -> new RuntimeException("No se encontró el pago asociado al PayPal Order ID: " + paypalOrderId));

        // Idempotencia: Si ya fue aprobado, devolver resultado existente
        if (pago.getStatus() == Pago.EstadoPago.APPROVED) {
            log.info("Pago PayPal {} ya se encuentra aprobado. Operación idempotente.", paypalOrderId);
            return PayPalCaptureResponse.builder()
                    .pagoId(pago.getId())
                    .ordenId(pago.getOrdenId())
                    .numeroOrden(pago.getNumeroOrden())
                    .paypalOrderId(pago.getPaypalOrderId())
                    .paypalCaptureId(pago.getPaypalCaptureId())
                    .status("COMPLETED")
                    .amount(pago.getAmount())
                    .currency(pago.getCurrency())
                    .timestamp(pago.getPaymentDate())
                    .build();
        }

        String token = getAccessToken();


        String captureUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v2/checkout/orders/" + paypalOrderId.trim() + "/capture";
        log.info("Capturando orden en PayPal Sandbox: {}", captureUrl);

        try {
            String responseBody = restClient.post()
                    .uri(captureUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.has("status") ? root.get("status").asText() : "";

            log.info("Respuesta de captura de PayPal para orden {}: status={}", paypalOrderId, status);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                String captureId = "";
                if (root.has("purchase_units") && root.get("purchase_units").size() > 0) {
                    JsonNode unit = root.get("purchase_units").get(0);
                    if (unit.has("payments") && unit.get("payments").has("captures") && unit.get("payments").get("captures").size() > 0) {
                        captureId = unit.get("payments").get("captures").get(0).get("id").asText();
                    }
                }

                pago.setStatus(Pago.EstadoPago.APPROVED);
                pago.setPaypalCaptureId(captureId);
                pago.setExternalStatus("COMPLETED");
                pago.setPaymentDate(LocalDateTime.now());
                pago = pagoRepository.save(pago);

                // Notificar a orden-ms para marcar orden PAGADA y descontar inventario
                ordenClient.confirmarPagoOrden(pago.getOrdenId(), captureId != null && !captureId.isEmpty() ? captureId : paypalOrderId);
                log.info("Pago aprobado confirmado en orden-ms para orden {}", pago.getOrdenId());

                return PayPalCaptureResponse.builder()
                        .pagoId(pago.getId())
                        .ordenId(pago.getOrdenId())
                        .numeroOrden(pago.getNumeroOrden())
                        .paypalOrderId(paypalOrderId)
                        .paypalCaptureId(captureId)
                        .status("COMPLETED")
                        .amount(pago.getAmount())
                        .currency(pago.getCurrency())
                        .timestamp(pago.getPaymentDate())
                        .build();

            } else {
                pago.setStatus(Pago.EstadoPago.REJECTED);
                pago.setExternalStatus(status);
                pagoRepository.save(pago);
                throw new PaymentGatewayException("La captura de PayPal no fue completada. Estado: " + status,
                        HttpStatus.BAD_REQUEST, "PAYPAL", true);
            }

        } catch (RestClientResponseException ex) {
            log.error("Error al capturar orden en PayPal Sandbox: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            pago.setStatus(Pago.EstadoPago.REJECTED);
            pagoRepository.save(pago);
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new PaymentGatewayAuthenticationException("PAYPAL", "Credenciales de PayPal Sandbox inválidas.");
            }
            throw new PaymentGatewayException("Error al capturar el pago en PayPal Sandbox: HTTP " + ex.getStatusCode().value(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al capturar pago PayPal: {}", ex.getMessage(), ex);
            throw new PaymentGatewayException("Error inesperado al capturar el pago: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, "PAYPAL", true);
        }
    }

    /**
     * Consult order details from PayPal Orders API v2
     */
    public JsonNode consultarOrdenPayPal(String paypalOrderId) {
        String token = getAccessToken();
        String orderUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v2/checkout/orders/" + paypalOrderId.trim();

        try {
            String responseBody = restClient.get()
                    .uri(orderUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            log.error("Error consultando orden en PayPal: {}", ex.getMessage());
            throw new RuntimeException("No se pudo consultar la orden en PayPal.");
        }
    }

    private PagoDTO mapToDTO(Pago p) {
        return PagoDTO.builder()
                .id(p.getId())
                .ordenId(p.getOrdenId())
                .numeroOrden(p.getNumeroOrden())
                .paypalOrderId(p.getPaypalOrderId())
                .paypalCaptureId(p.getPaypalCaptureId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .provider(p.getProvider())
                .externalStatus(p.getExternalStatus())
                .paymentDate(p.getPaymentDate())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
