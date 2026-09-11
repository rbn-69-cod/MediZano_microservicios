package com.medizano.pago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.MercadoPagoProperties;
import com.medizano.pago.dto.*;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import com.medizano.pago.exception.PaymentGatewayAuthenticationException;
import com.medizano.pago.exception.PaymentGatewayException;
import com.medizano.pago.exception.PaymentGatewayNotConfiguredException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoPaymentService {

    private final PagoRepository pagoRepository;
    private final OrdenClient ordenClient;
    private final MercadoPagoProperties mpProperties;
    private final ObjectMapper objectMapper;
    private final PaymentOrderConfirmationService confirmationService;
    private final RestTemplate restTemplate = new RestTemplate();

    public MercadoPagoProperties getProperties() {
        return this.mpProperties;
    }

    public boolean isConfigured() {
        return isUsableCredential(mpProperties.getAccessToken());
    }

    public String getPublicKeyForFrontend() {
        return isUsableCredential(mpProperties.getPublicKey()) ? mpProperties.getPublicKey() : "";
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
     * Crea una preferencia de pago en Mercado Pago Checkout Pro (POST /checkout/preferences)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public MercadoPagoPreferenceResponse crearPreferencia(MercadoPagoPreferenceRequest request) {
        if (request.getOrdenId() == null) {
            throw new IllegalArgumentException("El ID de la orden es obligatorio.");
        }

        // 1. Validar la orden con orden-ms
        OrdenClient.OrdenResponse orden = ordenClient.obtenerOrdenPorId(request.getOrdenId());
        if (orden == null) {
            throw new IllegalArgumentException("No se encontró la orden con ID: " + request.getOrdenId());
        }

        if ("PAGADA".equalsIgnoreCase(orden.getEstado())) {
            throw new IllegalStateException("Esta orden ya ha sido pagada previamente.");
        }
        if (!"MERCADO_PAGO".equalsIgnoreCase(orden.getMetodoPago())) {
            throw new IllegalStateException("La orden no fue creada para pago con Mercado Pago.");
        }

        Optional<Pago> existingPending = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(
                orden.getId(), "MERCADO_PAGO", Pago.EstadoPago.PENDING);
        if (existingPending.isPresent() && existingPending.get().getMpPreferenceId() != null
                && existingPending.get().getMpInitPoint() != null) {
            Pago existing = existingPending.get();
            return MercadoPagoPreferenceResponse.builder()
                    .ordenId(orden.getId())
                    .numeroOrden(orden.getNumeroOrden())
                    .preferenceId(existing.getMpPreferenceId())
                    .initPoint(existing.getMpInitPoint())
                    .sandboxInitPoint(existing.getMpSandboxInitPoint() != null
                            ? existing.getMpSandboxInitPoint() : existing.getMpInitPoint())
                    .amountPen(existing.getAmount())
                    .currency(existing.getCurrency())
                    .publicKey(mpProperties.getPublicKey())
                    .build();
        }

        boolean tokenPresent = isConfigured();
        boolean publicKeyPresent = isUsableCredential(mpProperties.getPublicKey());

        log.info("Verificación Mercado Pago: ACCESS_TOKEN={}, PUBLIC_KEY={}",
                tokenPresent ? "PRESENT" : "MISSING",
                publicKeyPresent ? "PRESENT" : "MISSING");

        if (!tokenPresent) {
            throw new PaymentGatewayNotConfiguredException("MERCADO_PAGO", "Mercado Pago no está configurado.");
        }

        String prefUrl = mpProperties.getBaseUrl().replaceAll("/+$", "") + "/checkout/preferences";
        log.info("Creando preferencia en Mercado Pago: {}", prefUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpProperties.getAccessToken().trim());
        headers.set("X-Idempotency-Key", "MEDIZANO-PREF-" + orden.getId());

        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("id", String.valueOf(orden.getId()));
        itemMap.put("title", "Orden MediZano #" + orden.getNumeroOrden());
        itemMap.put("description", "Venta de medicamentos en Botica MediZano");
        itemMap.put("quantity", 1);
        itemMap.put("currency_id", "PEN");
        itemMap.put("unit_price", orden.getTotal());

        Map<String, Object> backUrls = new HashMap<>();
        // Angular usa HashLocationStrategy; el retorno debe conservar la ruta dentro del hash.
        String backUrl = mpProperties.getCheckoutBaseUrl().replaceAll("/+$", "") + "/#/billing";
        backUrls.put("success", backUrl);
        backUrls.put("pending", backUrl);
        backUrls.put("failure", backUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.singletonList(itemMap));
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().trim().isEmpty()) {
            Map<String, Object> payerMap = new HashMap<>();
            payerMap.put("email", request.getCustomerEmail().trim());
            body.put("payer", payerMap);
        }
        body.put("external_reference", String.valueOf(orden.getId()));
        body.put("statement_descriptor", "MEDIZANO BOTICA");
        body.put("back_urls", backUrls);
        body.put("auto_return", "approved");
        body.put("notification_url", mpProperties.getCheckoutBaseUrl().replaceAll("/+$", "")
                + "/api/v1/pagos/mercadopago/webhook");


        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(prefUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Respuesta inválida de Mercado Pago al crear preferencia: HTTP {}", response.getStatusCode());
                throw new PaymentGatewayException("Respuesta inválida de Mercado Pago al crear preferencia.",
                        HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String preferenceId = root.path("id").asText();
            String initPoint = root.path("init_point").asText();
            String sandboxInitPoint = root.path("sandbox_init_point").asText();

            // Guardar registro de Pago en estado PENDING
            Pago pago = Pago.builder()
                    .ordenId(orden.getId())
                    .numeroOrden(orden.getNumeroOrden())
                    .mpPreferenceId(preferenceId)
                    .mpInitPoint(initPoint)
                    .mpSandboxInitPoint(sandboxInitPoint)
                    .amount(orden.getTotal())
                    .currency("PEN")
                    .provider("MERCADO_PAGO")
                    .status(Pago.EstadoPago.PENDING)
                    .build();

            pagoRepository.save(pago);
            log.info("Preferencia de Mercado Pago creada exitosamente: {} para orden {}", preferenceId, orden.getNumeroOrden());

            return MercadoPagoPreferenceResponse.builder()
                    .ordenId(orden.getId())
                    .numeroOrden(orden.getNumeroOrden())
                    .preferenceId(preferenceId)
                    .initPoint(initPoint)
                    .sandboxInitPoint(sandboxInitPoint != null && !sandboxInitPoint.isEmpty() ? sandboxInitPoint : initPoint)
                    .amountPen(orden.getTotal())
                    .currency("PEN")
                    .publicKey(mpProperties.getPublicKey())
                    .build();

        } catch (RestClientResponseException ex) {
            log.error("Error al crear preferencia en Mercado Pago: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new PaymentGatewayAuthenticationException("MERCADO_PAGO", "Credenciales de Mercado Pago inválidas o no autorizadas.");
            }
            throw new PaymentGatewayException("Error al comunicarse con Mercado Pago para crear la preferencia: HTTP " + ex.getStatusCode().value(),
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al crear preferencia Mercado Pago: {}", ex.getMessage(), ex);
            throw new PaymentGatewayException("Error inesperado al crear la preferencia en Mercado Pago: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        }
    }

    /**
     * Verifica el estado del pago con Mercado Pago API (GET /v1/payments/{id}) y confirma la orden si fue aprobado
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public MercadoPagoPaymentResponse verificarYConfirmarPago(MercadoPagoVerifyRequest request) {
        if (request.getPaymentId() == null || request.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("El Payment ID es obligatorio.");
        }

        String paymentId = request.getPaymentId().trim();

        // Idempotencia: Verificar si ya existe pago aprobado con este ID
        Optional<Pago> existingPago = pagoRepository.findByMpPaymentId(paymentId);
        if (existingPago.isPresent() && existingPago.get().getStatus() == Pago.EstadoPago.APPROVED) {
            Pago p = existingPago.get();
            if (!Boolean.TRUE.equals(p.getOrderConfirmed())) {
                confirmationService.confirmOrder(p, confirmationService.buildReference(p));
            }
            log.info("Pago Mercado Pago {} ya se encuentra aprobado. Operación idempotente.", paymentId);
            return MercadoPagoPaymentResponse.builder()
                    .pagoId(p.getId())
                    .ordenId(p.getOrdenId())
                    .numeroOrden(p.getNumeroOrden())
                    .paymentId(p.getMpPaymentId())
                    .preferenceId(p.getMpPreferenceId())
                    .status("approved")
                    .statusDetail(p.getExternalStatus())
                    .amount(p.getAmount())
                    .currency(p.getCurrency())
                    .timestamp(p.getPaymentDate())
                    .orderConfirmed(p.getOrderConfirmed())
                    .build();
        }

        if (!isConfigured()) {
            throw new PaymentGatewayNotConfiguredException("MERCADO_PAGO", "Mercado Pago no está configurado.");
        }

        String paymentUrl = mpProperties.getBaseUrl().replaceAll("/+$", "") + "/v1/payments/" + paymentId;
        log.info("Consultando pago en Mercado Pago: {}", paymentUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mpProperties.getAccessToken().trim());

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(paymentUrl, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("No se pudo obtener información del pago desde Mercado Pago.");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();
            String statusDetail = root.path("status_detail").asText();
            BigDecimal amount = new BigDecimal(root.path("transaction_amount").asText("0.00"));
            String currency = root.path("currency_id").asText("PEN");
            String externalReference = root.path("external_reference").asText();

            Long ordenId = request.getOrdenId();
            if (ordenId == null && !externalReference.isEmpty()) {
                try {
                    ordenId = Long.parseLong(externalReference);
                } catch (NumberFormatException ignored) {}
            }

            Pago pago = null;
            if (request.getPreferenceId() != null && !request.getPreferenceId().isEmpty()) {
                pago = pagoRepository.findByMpPreferenceId(request.getPreferenceId()).orElse(null);
            }
            if (pago == null && ordenId != null) {
                pago = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(
                        ordenId, "MERCADO_PAGO", Pago.EstadoPago.PENDING).orElse(null);
            }
            if (pago == null) {
                throw new IllegalArgumentException("El pago no corresponde a una preferencia creada por MediZano.");
            }

            validarPagoContraPreferencia(pago, ordenId, externalReference, amount, currency);

            pago.setMpPaymentId(paymentId);
            pago.setExternalStatus(statusDetail);

            if ("approved".equalsIgnoreCase(status)) {
                pago.setStatus(Pago.EstadoPago.APPROVED);
                pago.setPaymentDate(LocalDateTime.now());
                pago = pagoRepository.save(pago);
                confirmationService.confirmOrder(pago, "MP-" + paymentId);
            } else if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
                pago.setStatus(Pago.EstadoPago.REJECTED);
                pago = pagoRepository.save(pago);
                log.warn("Pago Mercado Pago {} fue rechazado ({}) para orden {}", paymentId, statusDetail, pago.getOrdenId());
            } else {
                pago.setStatus(Pago.EstadoPago.PENDING);
                pago = pagoRepository.save(pago);
                log.info("Pago Mercado Pago {} en estado pendiente/en proceso: {}", paymentId, status);
            }

            return MercadoPagoPaymentResponse.builder()
                    .pagoId(pago.getId())
                    .ordenId(pago.getOrdenId())
                    .numeroOrden(pago.getNumeroOrden())
                    .paymentId(paymentId)
                    .preferenceId(pago.getMpPreferenceId())
                    .status(status)
                    .statusDetail(statusDetail)
                    .amount(amount)
                    .currency(currency)
                    .timestamp(pago.getPaymentDate() != null ? pago.getPaymentDate() : LocalDateTime.now())
                    .orderConfirmed(pago.getOrderConfirmed())
                    .build();

        } catch (RestClientResponseException ex) {
            log.error("Error al consultar pago en Mercado Pago: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new PaymentGatewayAuthenticationException("MERCADO_PAGO", "Credenciales de Mercado Pago inválidas.");
            }
            throw new PaymentGatewayException("Error al comunicarse con Mercado Pago para verificar el pago: HTTP " + ex.getStatusCode().value(),
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        } catch (SecurityException ex) {
            throw ex;
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error inesperado al verificar pago Mercado Pago: {}", ex.getMessage(), ex);
            throw new PaymentGatewayException("Error inesperado al verificar el pago en Mercado Pago: " + ex.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        }
    }

    /**
     * Reconcilia una preferencia cuando el navegador solo conoce preference_id.
     * La búsqueda se hace en Mercado Pago y cada resultado vuelve a pasar por la
     * validación estricta de orden, monto y moneda.
     */
    public MercadoPagoPaymentResponse reconciliarPreferencia(String preferenceId) {
        Pago pago = pagoRepository.findByMpPreferenceId(preferenceId)
                .orElseThrow(() -> new IllegalArgumentException("Preferencia de Mercado Pago no registrada."));

        if (pago.getStatus() == Pago.EstadoPago.APPROVED && pago.getMpPaymentId() != null) {
            return verificarYConfirmarPago(MercadoPagoVerifyRequest.builder()
                    .ordenId(pago.getOrdenId())
                    .preferenceId(preferenceId)
                    .paymentId(pago.getMpPaymentId())
                    .build());
        }

        if (!isConfigured()) {
            throw new PaymentGatewayNotConfiguredException("MERCADO_PAGO", "Mercado Pago no está configurado.");
        }

        String searchUrl = mpProperties.getBaseUrl().replaceAll("/+$", "")
                + "/v1/payments/search?external_reference=" + pago.getOrdenId()
                + "&sort=date_created&criteria=desc";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mpProperties.getAccessToken().trim());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode results = objectMapper.readTree(response.getBody()).path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new IllegalStateException("Mercado Pago aún no informa un pago para esta preferencia.");
            }

            for (JsonNode result : results) {
                String candidateId = result.path("id").asText();
                String candidateStatus = result.path("status").asText();
                if (!candidateId.isBlank() && "approved".equalsIgnoreCase(candidateStatus)) {
                    return verificarYConfirmarPago(MercadoPagoVerifyRequest.builder()
                            .ordenId(pago.getOrdenId())
                            .preferenceId(preferenceId)
                            .paymentId(candidateId)
                            .build());
                }
            }
            throw new IllegalStateException("El pago todavía está pendiente de aprobación en Mercado Pago.");
        } catch (RestClientResponseException ex) {
            throw new PaymentGatewayException("No se pudo reconciliar la preferencia en Mercado Pago.",
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        } catch (PaymentGatewayException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("Respuesta inválida al reconciliar Mercado Pago.",
                    HttpStatus.SERVICE_UNAVAILABLE, "MERCADO_PAGO", true);
        }
    }

    private void validarPagoContraPreferencia(Pago pago, Long ordenIdSolicitada, String externalReference,
                                               BigDecimal amount, String currency) {
        if (ordenIdSolicitada != null && !pago.getOrdenId().equals(ordenIdSolicitada)) {
            marcarRevision(pago, "La orden solicitada no coincide con la preferencia.");
        }
        if (externalReference == null || !String.valueOf(pago.getOrdenId()).equals(externalReference.trim())) {
            marcarRevision(pago, "La referencia externa no coincide con la orden.");
        }
        if (pago.getAmount() == null || pago.getAmount().compareTo(amount) != 0) {
            marcarRevision(pago, "El monto acreditado no coincide con el monto esperado.");
        }
        if (pago.getCurrency() == null || !pago.getCurrency().equalsIgnoreCase(currency)) {
            marcarRevision(pago, "La moneda acreditada no coincide con la moneda esperada.");
        }
    }

    private void marcarRevision(Pago pago, String reason) {
        pago.setStatus(Pago.EstadoPago.REVIEW_REQUIRED);
        pago.setLastConfirmationError(reason);
        pagoRepository.save(pago);
        throw new SecurityException(reason);
    }

    /**
     * Valida la firma HMAC-SHA256 enviada por Mercado Pago en el header x-signature
     */
    public boolean validarFirmaWebhook(String xSignature, String xRequestId, String dataId) {
        String secret = mpProperties.getWebhookSecret();
        if (!isUsableCredential(secret)) {
            log.error("MERCADOPAGO_WEBHOOK_SECRET no está configurado.");
            return false;
        }

        if (xSignature == null || xSignature.trim().isEmpty()) {
            log.error("Falta el header x-signature en la notificación webhook.");
            return false;
        }

        try {
            String ts = null;
            String hashRecibido = null;
            String[] parts = xSignature.split(",");
            for (String part : parts) {
                String[] keyValue = part.trim().split("=", 2);
                if (keyValue.length == 2) {
                    if ("ts".equalsIgnoreCase(keyValue[0])) {
                        ts = keyValue[1];
                    } else if ("v1".equalsIgnoreCase(keyValue[0])) {
                        hashRecibido = keyValue[1];
                    }
                }
            }

            if (ts == null || hashRecibido == null) {
                log.error("Formato de x-signature inválido: {}", xSignature);
                return false;
            }

            long timestamp = Long.parseLong(ts);
            long timestampMillis = ts.length() <= 10 ? timestamp * 1000L : timestamp;
            if (Math.abs(System.currentTimeMillis() - timestampMillis) > 5 * 60 * 1000L) {
                log.error("Webhook de Mercado Pago rechazado por timestamp fuera de ventana.");
                return false;
            }

            // Manifest formato Mercado Pago: id:[data.id];request-id:[x-request-id];ts:[ts];
            String manifest = "id:" + (dataId != null ? dataId : "") + ";request-id:" + (xRequestId != null ? xRequestId : "") + ";ts:" + ts + ";";

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(secret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            String hashCalculado = sb.toString();

            boolean valid = java.security.MessageDigest.isEqual(
                    hashCalculado.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    hashRecibido.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if (!valid) {
                log.error("Firma HMAC de webhook no coincide.");
            }
            return valid;
        } catch (Exception ex) {
            log.error("Error al validar firma HMAC de Mercado Pago: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Procesa notificaciones Webhook de Mercado Pago
     */
    @Transactional
    public void procesarWebhook(Map<String, Object> payload) {
        procesarWebhook(payload, null, null, null, null);
    }

    /**
     * Procesa notificaciones Webhook de Mercado Pago con validación HMAC
     */
    @Transactional
    public void procesarWebhook(Map<String, Object> payload, String xSignature, String xRequestId) {
        procesarWebhook(payload, xSignature, xRequestId, null, null);
    }

    /**
     * Mercado Pago firma el valor de data.id recibido en el query string. El
     * cuerpo se conserva como respaldo para compatibilidad con simulaciones
     * antiguas, pero nunca sustituye al query param cuando este está presente.
     */
    @Transactional
    public void procesarWebhook(Map<String, Object> payload, String xSignature, String xRequestId,
                                String queryDataId, String queryType) {
        if (payload == null) return;

        String type = queryType != null && !queryType.isBlank()
                ? queryType
                : String.valueOf(payload.getOrDefault("type", payload.getOrDefault("topic", "")));
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String paymentId = queryDataId != null && !queryDataId.isBlank() ? queryDataId : null;

        if (paymentId == null && data != null && data.get("id") != null) {
            paymentId = String.valueOf(data.get("id"));
        } else if (paymentId == null && payload.get("id") != null) {
            paymentId = String.valueOf(payload.get("id"));
        }

        if (!validarFirmaWebhook(xSignature, xRequestId, paymentId)) {
            log.error("Rechazando webhook de Mercado Pago por firma HMAC inválida.");
            throw new SecurityException("Firma HMAC de Mercado Pago no válida.");
        }

        log.info("Webhook válido recibido de Mercado Pago: type={}, paymentId={}", type, paymentId);

        if (paymentId != null && ("payment".equalsIgnoreCase(type) || "payment.created".equalsIgnoreCase(type) || "payment.updated".equalsIgnoreCase(type))) {
            verificarYConfirmarPago(MercadoPagoVerifyRequest.builder().paymentId(paymentId).build());
        }
    }
}
