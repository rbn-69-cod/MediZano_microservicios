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
    private final PaymentOrderConfirmationService confirmationService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private String cachedToken = null;
    private Instant tokenExpiry = Instant.MIN;

    public PayPalService(PagoRepository pagoRepository,
                         OrdenClient ordenClient,
                         PayPalProperties payPalProperties,
                         ObjectMapper objectMapper,
                         PaymentOrderConfirmationService confirmationService) {
        this.pagoRepository = pagoRepository;
        this.ordenClient = ordenClient;
        this.payPalProperties = payPalProperties;
        this.objectMapper = objectMapper;
        this.confirmationService = confirmationService;
        this.restClient = RestClient.builder().build();
    }

    public PayPalConfigResponse obtenerConfiguracionPublica() {
        return PayPalConfigResponse.builder()
                .clientId(isUsableCredential(payPalProperties.getClientId()) ? payPalProperties.getClientId() : "")
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
        return isUsableCredential(payPalProperties.getClientId())
                && isUsableCredential(payPalProperties.getClientSecret());
    }

    private boolean isUsableCredential(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("placeholder")
                && !normalized.contains("replace")
                && !normalized.contains("your_")
                && !normalized.contains("your-")
                && !normalized.endsWith("_here")
                && !normalized.endsWith("-here")
                && !normalized.startsWith("tu_");
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
        boolean idPresent = isUsableCredential(clientId);
        boolean secretPresent = isUsableCredential(clientSecret);

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
        if (!"PAYPAL".equalsIgnoreCase(orden.getMetodoPago())) {
            throw new IllegalStateException("La orden no fue creada para pago con PayPal.");
        }

        String token = getAccessToken();


        // Calcular conversión PEN a USD
        BigDecimal amountPen = orden.getTotal();
        BigDecimal exchangeRate = payPalProperties.getExchangeRate() != null && payPalProperties.getExchangeRate().compareTo(BigDecimal.ZERO) > 0
                ? payPalProperties.getExchangeRate()
                : new BigDecimal("3.75");
        BigDecimal amountUsd = amountPen.divide(exchangeRate, 2, RoundingMode.HALF_UP);

        Optional<Pago> existingPending = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(
                orden.getId(), "PAYPAL", Pago.EstadoPago.PENDING);
        if (existingPending.isPresent() && existingPending.get().getPaypalOrderId() != null
                && existingPending.get().getPaypalApproveUrl() != null) {
            Pago existing = existingPending.get();
            return PayPalOrderResponse.builder()
                    .ordenId(orden.getId())
                    .numeroOrden(orden.getNumeroOrden())
                    .paypalOrderId(existing.getPaypalOrderId())
                    .status(existing.getExternalStatus())
                    .approveUrl(existing.getPaypalApproveUrl())
                    .amountPen(amountPen)
                    .amountUsd(existing.getAmount())
                    .currency(existing.getCurrency())
                    .clientId(payPalProperties.getClientId())
                    .build();
        }

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

        String checkoutBaseUrl = payPalProperties.getCheckoutBaseUrl().replaceAll("/+$", "");
        Map<String, Object> applicationContext = new HashMap<>();
        applicationContext.put("brand_name", "MEDIZANO BOTICA");
        applicationContext.put("landing_page", "LOGIN");
        applicationContext.put("user_action", "PAY_NOW");
        // El frontend usa HashLocationStrategy; el fragmento garantiza que el retorno
        // de PayPal cargue la ruta Angular en lugar de una ruta vacia del servidor.
        applicationContext.put("return_url", checkoutBaseUrl + "/#/billing?payment=paypal-approved");
        applicationContext.put("cancel_url", checkoutBaseUrl + "/#/billing?payment=paypal-cancelled");
        orderPayload.put("application_context", applicationContext);

        String ordersUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v2/checkout/orders";
        String requestId = "MEDIZANO-ORDER-" + orden.getId();

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
            Pago pago = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(
                            orden.getId(), "PAYPAL", Pago.EstadoPago.PENDING)
                    .orElse(Pago.builder()
                            .ordenId(orden.getId())
                            .numeroOrden(orden.getNumeroOrden())
                            .provider("PAYPAL")
                            .currency(payPalProperties.getCurrency())
                            .build());

            pago.setAmount(amountUsd);
            pago.setPaypalOrderId(paypalOrderId);
            pago.setPaypalApproveUrl(approveUrl);
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
            if (!Boolean.TRUE.equals(pago.getOrderConfirmed())) {
                confirmationService.confirmOrder(pago, confirmationService.buildReference(pago));
            }
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
                    .orderConfirmed(pago.getOrderConfirmed())
                    .build();
        }

        String token = getAccessToken();


        String captureUrl = payPalProperties.getBaseUrl().replaceAll("/+$", "") + "/v2/checkout/orders/" + paypalOrderId.trim() + "/capture";
        log.info("Capturando orden en PayPal Sandbox: {}", captureUrl);

        try {
            String responseBody = restClient.post()
                    .uri(captureUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("PayPal-Request-Id", "MEDIZANO-CAPTURE-" + paypalOrderId.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.has("status") ? root.get("status").asText() : "";

            log.info("Respuesta de captura de PayPal para orden {}: status={}", paypalOrderId, status);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                // La respuesta de captura es la fuente inmediata y autoritativa. Usarla
                // evita una lectura GET eventualmente inconsistente justo después de
                // capturar, que antes podía retener falsamente un pago para revisión.
                return registrarCapturaCompletada(pago, root);

            } else {
                pago.setStatus(Pago.EstadoPago.REJECTED);
                pago.setExternalStatus(status);
                pagoRepository.save(pago);
                throw new PaymentGatewayException("La captura de PayPal no fue completada. Estado: " + status,
                        HttpStatus.BAD_REQUEST, "PAYPAL", true);
            }

        } catch (RestClientResponseException ex) {
            log.error("Error al capturar orden en PayPal Sandbox: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 422) {
                JsonNode currentOrder = consultarOrdenPayPal(paypalOrderId);
                if ("COMPLETED".equalsIgnoreCase(currentOrder.path("status").asText())) {
                    return registrarCapturaCompletada(pago, currentOrder);
                }
            }
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
     * Consulta el estado oficial de PayPal y captura automáticamente apenas el
     * comprador aprueba la orden. Los estados no aprobados se devuelven sin
     * modificar inventario ni marcar la venta como pagada.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public PayPalCaptureResponse reconciliarOrden(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.trim().isEmpty()) {
            throw new IllegalArgumentException("El PayPal Order ID es obligatorio.");
        }

        String normalizedOrderId = paypalOrderId.trim();
        Pago pago = pagoRepository.findByPaypalOrderId(normalizedOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el pago asociado al PayPal Order ID: " + normalizedOrderId));

        if (pago.getStatus() == Pago.EstadoPago.APPROVED) {
            return capturarOrden(normalizedOrderId);
        }
        if (pago.getStatus() == Pago.EstadoPago.CANCELLED || pago.getStatus() == Pago.EstadoPago.REJECTED) {
            return buildPayPalStatusResponse(pago, pago.getExternalStatus());
        }

        JsonNode remoteOrder = consultarOrdenPayPal(normalizedOrderId);
        String remoteStatus = remoteOrder.path("status").asText("CREATED").toUpperCase(Locale.ROOT);
        pago.setExternalStatus(remoteStatus);

        if ("APPROVED".equals(remoteStatus)) {
            pagoRepository.save(pago);
            return capturarOrden(normalizedOrderId);
        }
        if ("COMPLETED".equals(remoteStatus)) {
            return registrarCapturaCompletada(pago, remoteOrder);
        }
        if ("VOIDED".equals(remoteStatus)) {
            pago.setStatus(Pago.EstadoPago.CANCELLED);
        } else {
            pago.setStatus(Pago.EstadoPago.PENDING);
        }

        pago = pagoRepository.save(pago);
        return buildPayPalStatusResponse(pago, remoteStatus);
    }

    private PayPalCaptureResponse buildPayPalStatusResponse(Pago pago, String status) {
        return PayPalCaptureResponse.builder()
                .pagoId(pago.getId())
                .ordenId(pago.getOrdenId())
                .numeroOrden(pago.getNumeroOrden())
                .paypalOrderId(pago.getPaypalOrderId())
                .paypalCaptureId(pago.getPaypalCaptureId())
                .status(status)
                .amount(pago.getAmount())
                .currency(pago.getCurrency())
                .timestamp(pago.getPaymentDate() != null ? pago.getPaymentDate() : pago.getUpdatedAt())
                .orderConfirmed(pago.getOrderConfirmed())
                .build();
    }

    private PayPalCaptureResponse registrarCapturaCompletada(Pago pago, JsonNode root) {
        JsonNode unit = root.path("purchase_units").path(0);
        JsonNode capture = unit.path("payments").path("captures").path(0);
        String captureId = capture.path("id").asText();
        String capturedCurrency = capture.path("amount").path("currency_code").asText();
        BigDecimal capturedAmount = new BigDecimal(capture.path("amount").path("value").asText("0"));
        String customId = unit.path("custom_id").asText();

        boolean invalid = captureId.isBlank()
                || !String.valueOf(pago.getOrdenId()).equals(customId)
                || pago.getAmount().compareTo(capturedAmount) != 0
                || !pago.getCurrency().equalsIgnoreCase(capturedCurrency);
        if (invalid) {
            pago.setStatus(Pago.EstadoPago.REVIEW_REQUIRED);
            pago.setExternalStatus("COMPLETED_AMOUNT_OR_ORDER_MISMATCH");
            pago.setLastConfirmationError("La captura PayPal no coincide con la orden, monto o moneda esperados.");
            pagoRepository.save(pago);
            throw new PaymentGatewayException("La captura fue retenida para revisión por datos inconsistentes.",
                    HttpStatus.CONFLICT, "PAYPAL", false);
        }

        pago.setStatus(Pago.EstadoPago.APPROVED);
        pago.setPaypalCaptureId(captureId);
        pago.setExternalStatus("COMPLETED");
        pago.setPaymentDate(LocalDateTime.now());
        pago = pagoRepository.save(pago);
        confirmationService.confirmOrder(pago, "PAYPAL-" + captureId);

        return PayPalCaptureResponse.builder()
                .pagoId(pago.getId())
                .ordenId(pago.getOrdenId())
                .numeroOrden(pago.getNumeroOrden())
                .paypalOrderId(pago.getPaypalOrderId())
                .paypalCaptureId(captureId)
                .status("COMPLETED")
                .amount(pago.getAmount())
                .currency(pago.getCurrency())
                .timestamp(pago.getPaymentDate())
                .orderConfirmed(pago.getOrderConfirmed())
                .build();
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
                .orderConfirmed(p.getOrderConfirmed())
                .confirmationAttempts(p.getConfirmationAttempts())
                .lastConfirmationError(p.getLastConfirmationError())
                .paymentDate(p.getPaymentDate())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
