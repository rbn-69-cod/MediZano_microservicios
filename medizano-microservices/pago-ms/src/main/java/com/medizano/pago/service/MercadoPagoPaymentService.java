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
    private final RestTemplate restTemplate = new RestTemplate();

    public MercadoPagoProperties getProperties() {
        return this.mpProperties;
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

        if (mpProperties.getAccessToken() == null || mpProperties.getAccessToken().trim().isEmpty()) {
            log.error("Credencial MERCADOPAGO_ACCESS_TOKEN no configurada.");
            throw new IllegalStateException("Mercado Pago no está configurado en el servidor (falta MERCADOPAGO_ACCESS_TOKEN).");
        }

        String prefUrl = mpProperties.getBaseUrl().replaceAll("/+$", "") + "/checkout/preferences";
        log.info("Creando preferencia en Mercado Pago: {}", prefUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpProperties.getAccessToken().trim());
        headers.set("X-Idempotency-Key", "MP-PREF-" + orden.getId() + "-" + System.currentTimeMillis());

        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("id", String.valueOf(orden.getId()));
        itemMap.put("title", "Orden MediZano #" + orden.getNumeroOrden());
        itemMap.put("description", "Venta de medicamentos en Botica MediZano");
        itemMap.put("quantity", 1);
        itemMap.put("currency_id", "PEN");
        itemMap.put("unit_price", orden.getTotal());

        Map<String, Object> payerMap = new HashMap<>();
        payerMap.put("email", request.getCustomerEmail() != null && !request.getCustomerEmail().isEmpty() ? request.getCustomerEmail() : "cliente@medizano.pe");

        Map<String, Object> backUrls = new HashMap<>();
        String backUrl = (request.getBackUrl() != null && !request.getBackUrl().isEmpty()) ? request.getBackUrl() : "http://localhost:4200/billing";
        backUrls.put("success", backUrl);
        backUrls.put("pending", backUrl);
        backUrls.put("failure", backUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.singletonList(itemMap));
        body.put("payer", payerMap);
        body.put("external_reference", String.valueOf(orden.getId()));
        body.put("statement_descriptor", "MEDIZANO BOTICA");
        body.put("back_urls", backUrls);


        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(prefUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Respuesta inválida de Mercado Pago al crear preferencia: HTTP {}", response.getStatusCode());
                throw new RuntimeException("Respuesta inválida de Mercado Pago al crear preferencia.");
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
            throw new RuntimeException("Error al comunicarse con Mercado Pago para crear la preferencia.");
        } catch (Exception ex) {
            log.error("Error inesperado al crear preferencia Mercado Pago: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error inesperado al crear la preferencia en Mercado Pago.");
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
                    .build();
        }

        if (mpProperties.getAccessToken() == null || mpProperties.getAccessToken().trim().isEmpty()) {
            throw new IllegalStateException("Mercado Pago no está configurado en el servidor.");
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
                List<Pago> pagosOrden = pagoRepository.findByOrdenIdOrderByCreatedAtDesc(ordenId);
                if (!pagosOrden.isEmpty()) {
                    pago = pagosOrden.get(0);
                }
            }
            if (pago == null) {
                if (ordenId == null) {
                    log.error("No se pudo asociar el pago de Mercado Pago {} a ninguna orden válida", paymentId);
                    throw new IllegalArgumentException("No se encontró una orden válida asociada al pago " + paymentId);
                }
                // Validar existencia de orden en orden-ms
                try {
                    OrdenClient.OrdenResponse ordenExistente = ordenClient.obtenerOrdenPorId(ordenId);
                    if (ordenExistente == null) {
                        throw new IllegalArgumentException("La orden con ID " + ordenId + " no existe en el sistema.");
                    }
                } catch (Exception ex) {
                    log.error("Error al validar orden {} para pago Mercado Pago {}: {}", ordenId, paymentId, ex.getMessage());
                    throw new RuntimeException("Error al validar la orden asociada: " + ex.getMessage());
                }

                pago = Pago.builder()
                        .ordenId(ordenId)
                        .amount(amount)
                        .currency(currency)
                        .provider("MERCADO_PAGO")
                        .build();
            }

            pago.setMpPaymentId(paymentId);
            pago.setExternalStatus(statusDetail);

            if ("approved".equalsIgnoreCase(status)) {
                pago.setStatus(Pago.EstadoPago.APPROVED);
                pago.setPaymentDate(LocalDateTime.now());
                pago = pagoRepository.save(pago);

                // Notificar a orden-ms para marcar PAGADA y descontar inventario
                try {
                    ordenClient.confirmarPagoOrden(pago.getOrdenId(), "MP-" + paymentId);
                    log.info("Orden {} confirmada exitosamente tras pago aprobado en Mercado Pago", pago.getOrdenId());
                } catch (Exception ex) {
                    log.error("Error al notificar a orden-ms sobre pago aprobado en Mercado Pago: {}", ex.getMessage());
                }
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
                    .build();

        } catch (RestClientResponseException ex) {
            log.error("Error al consultar pago en Mercado Pago: HTTP {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Error al comunicarse con Mercado Pago para verificar el pago.");
        } catch (Exception ex) {
            log.error("Error inesperado al verificar pago Mercado Pago: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error inesperado al verificar el pago en Mercado Pago.");
        }
    }

    /**
     * Valida la firma HMAC-SHA256 enviada por Mercado Pago en el header x-signature
     */
    public boolean validarFirmaWebhook(String xSignature, String xRequestId, String dataId) {
        String secret = mpProperties.getWebhookSecret();
        if (secret == null || secret.trim().isEmpty()) {
            log.warn("MERCADOPAGO_WEBHOOK_SECRET no configurado. Se omite validación HMAC (modo desarrollo).");
            return true;
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

            boolean valid = hashCalculado.equalsIgnoreCase(hashRecibido);
            if (!valid) {
                log.error("Firma HMAC no coincide. Esperado: {}, Recibido: {}", hashCalculado, hashRecibido);
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
        procesarWebhook(payload, null, null);
    }

    /**
     * Procesa notificaciones Webhook de Mercado Pago con validación HMAC
     */
    @Transactional
    public void procesarWebhook(Map<String, Object> payload, String xSignature, String xRequestId) {
        log.info("Webhook recibido de Mercado Pago: {}", payload);
        if (payload == null) return;

        String type = String.valueOf(payload.getOrDefault("type", payload.getOrDefault("topic", "")));
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String paymentId = null;

        if (data != null && data.get("id") != null) {
            paymentId = String.valueOf(data.get("id"));
        } else if (payload.get("id") != null) {
            paymentId = String.valueOf(payload.get("id"));
        }

        if (xSignature != null && !validarFirmaWebhook(xSignature, xRequestId, paymentId)) {
            log.error("Rechazando webhook de Mercado Pago por firma HMAC inválida.");
            throw new SecurityException("Firma HMAC de Mercado Pago no válida.");
        }

        if (paymentId != null && ("payment".equalsIgnoreCase(type) || "payment.created".equalsIgnoreCase(type) || "payment.updated".equalsIgnoreCase(type))) {
            try {
                verificarYConfirmarPago(MercadoPagoVerifyRequest.builder().paymentId(paymentId).build());
            } catch (Exception ex) {
                log.error("Error al procesar webhook de pago Mercado Pago {}: {}", paymentId, ex.getMessage());
            }
        }
    }
}

