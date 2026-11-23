package com.medizano.pago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.CulqiProperties;
import com.medizano.pago.dto.CulqiChargeRequest;
import com.medizano.pago.dto.CulqiChargeResponse;
import com.medizano.pago.dto.CulqiOrderResponse;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.exception.PaymentGatewayException;
import com.medizano.pago.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CulqiPaymentService {

    private final PagoRepository pagoRepository;
    private final OrdenClient ordenClient;
    private final CulqiProperties culqiProperties;
    private final PaymentOrderConfirmationService confirmationService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public CulqiProperties getProperties() {
        return this.culqiProperties;
    }

    public String getPublicKey() {
        return culqiProperties.getPublicKey();
    }

    /**
     * Creates Culqi's remote payment order. It is deliberately separate from a
     * card charge: the resulting ord_* is what enables QR/wallet payments in
     * Culqi Checkout.
     */
    @Transactional
    public CulqiOrderResponse crearOrdenQr(Long ordenId) {
        OrdenClient.OrdenResponse orden;
        try {
            orden = ordenClient.obtenerOrdenPorId(ordenId);
        } catch (Exception ex) {
            throw new PaymentGatewayException("No se encontró la orden especificada para el pago.", HttpStatus.NOT_FOUND, "CULQI", true);
        }
        if (orden == null || orden.getTotal() == null || orden.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("La orden no tiene un monto válido para generar el QR.", HttpStatus.BAD_REQUEST, "CULQI", true);
        }
        if ("PAGADA".equalsIgnoreCase(orden.getEstado())) {
            throw new PaymentGatewayException("La orden ya se encuentra pagada.", HttpStatus.CONFLICT, "CULQI", true);
        }
        if (!"PEN".equalsIgnoreCase(culqiProperties.getCurrency())) {
            throw new PaymentGatewayException("Las billeteras con QR de Culqi sólo admiten PEN.", HttpStatus.BAD_REQUEST, "CULQI", true);
        }
        if (orden.getTotal().compareTo(new BigDecimal("6.00")) < 0 || orden.getTotal().compareTo(new BigDecimal("500.00")) > 0) {
            throw new PaymentGatewayException("El pago por QR admite montos entre S/ 6.00 y S/ 500.00.", HttpStatus.BAD_REQUEST, "CULQI", true);
        }
        if (culqiProperties.getSecretKey() == null || culqiProperties.getSecretKey().isBlank()) {
            throw new PaymentGatewayException("CULQI_SECRET_KEY no está configurada.", HttpStatus.SERVICE_UNAVAILABLE, "CULQI", false);
        }

        Pago existing = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(
                        ordenId, "CULQI", Pago.EstadoPago.PENDING)
                .filter(p -> p.getCulqiOrderId() != null && p.getCulqiOrderExpiresAt() != null
                        && p.getCulqiOrderExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(null);
        if (existing != null) {
            return orderResponse(existing, orden);
        }

        String email = orden.getClienteEmail();
        String phone = normalizePhone(orden.getClienteTelefono());
        if (email == null || !email.contains("@") || phone == null) {
            throw new PaymentGatewayException("Para generar el QR ingresa correo y celular válido del cliente.", HttpStatus.BAD_REQUEST, "CULQI", true);
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(Math.max(5, culqiProperties.getOrderExpirationMinutes()));
        Map<String, Object> clientDetails = new HashMap<>();
        clientDetails.put("first_name", firstName(orden.getClienteNombre()));
        clientDetails.put("last_name", lastName(orden.getClienteNombre()));
        clientDetails.put("email", email);
        clientDetails.put("phone_number", phone);

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", toCents(orden.getTotal()));
        payload.put("currency_code", "PEN");
        payload.put("description", "Venta Farmacia MediZano - Orden " + orden.getNumeroOrden());
        payload.put("order_number", orden.getNumeroOrden());
        payload.put("client_details", clientDetails);
        payload.put("expiration_date", expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond());
        payload.put("metadata", Map.of("medizano_order_id", String.valueOf(orden.getId()), "medizano_order_number", orden.getNumeroOrden()));

        JsonNode response = callCulqi(HttpMethod.POST, "/orders", payload);
        if (response == null || !response.hasNonNull("id")) {
            throw new PaymentGatewayException("Culqi no devolvió un identificador de orden.", HttpStatus.BAD_GATEWAY, "CULQI", true);
        }

        Pago pago = Pago.builder()
                .ordenId(orden.getId()).numeroOrden(orden.getNumeroOrden())
                .culqiOrderId(response.get("id").asText())
                .culqiOrderExpiresAt(expiresAt).amount(orden.getTotal()).currency("PEN")
                .status(Pago.EstadoPago.PENDING).provider("CULQI")
                .externalStatus(readState(response)).build();
        return orderResponse(pagoRepository.save(pago), orden);
    }

    /** Webhooks are verified by querying Culqi with the merchant secret. */
    @Transactional
    public void procesarWebhook(JsonNode event) {
        String eventType = event.path("type").asText("");
        String culqiOrderId = event.path("data").path("id").asText("");
        if (culqiOrderId.isBlank() || (!eventType.isBlank() && !"order.status.changed".equals(eventType))) {
            log.info("Webhook Culqi ignorado: type={}", eventType);
            return;
        }
        Pago pago = pagoRepository.findByCulqiOrderId(culqiOrderId).orElse(null);
        if (pago == null) {
            log.warn("Webhook Culqi recibido para orden remota no registrada: {}", culqiOrderId);
            return;
        }
        JsonNode remoteOrder = callCulqi(HttpMethod.GET, "/orders/" + culqiOrderId, null);
        if (remoteOrder == null) {
            throw new PaymentGatewayException("No se pudo verificar la orden notificada por Culqi.", HttpStatus.BAD_GATEWAY, "CULQI", true);
        }
        int remoteAmount = remoteOrder.path("amount").asInt(-1);
        if (remoteAmount != toCents(pago.getAmount()) || !"PEN".equalsIgnoreCase(remoteOrder.path("currency_code").asText())) {
            throw new SecurityException("La orden de Culqi no coincide con el importe o moneda registrados.");
        }
        String state = readState(remoteOrder);
        pago.setExternalStatus(state);
        if (isPaid(state)) {
            pago.setStatus(Pago.EstadoPago.APPROVED);
            if (pago.getPaymentDate() == null) pago.setPaymentDate(LocalDateTime.now());
            pago = pagoRepository.save(pago);
            confirmationService.confirmOrder(pago, "CULQI-ORDER-" + culqiOrderId);
        } else if (isTerminalFailure(state)) {
            pago.setStatus(Pago.EstadoPago.CANCELLED);
            pagoRepository.save(pago);
        } else {
            pagoRepository.save(pago);
        }
    }

    private JsonNode callCulqi(HttpMethod method, String path, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(culqiProperties.getSecretKey());
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(culqiProperties.getBaseUrl() + path, method,
                    new HttpEntity<>(payload, headers), JsonNode.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Error Culqi {} {}: {}", method, path, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException("Culqi rechazó la operación: " + culqiError(ex), HttpStatus.valueOf(ex.getStatusCode().value()), "CULQI", true);
        } catch (Exception ex) {
            throw new PaymentGatewayException("No se pudo comunicar con Culqi.", HttpStatus.SERVICE_UNAVAILABLE, "CULQI", true);
        }
    }

    private String culqiError(HttpStatusCodeException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return body.path("user_message").asText(body.path("merchant_message").asText("Error remoto"));
        } catch (Exception ignored) { return "Error remoto"; }
    }

    private int toCents(BigDecimal amount) { return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact(); }
    private String firstName(String name) { return (name == null || name.isBlank()) ? "Cliente" : name.trim().split("\\s+")[0]; }
    private String lastName(String name) { return (name == null || name.trim().split("\\s+").length < 2) ? "MediZano" : name.trim().substring(name.trim().indexOf(' ') + 1); }
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("51")) digits = digits.substring(2);
        return digits.matches("9\\d{8}") ? "+51" + digits : null;
    }
    private String readState(JsonNode node) { return node.path("state").asText(node.path("status").asText("pending")).toLowerCase(); }
    private boolean isPaid(String state) { return "paid".equals(state) || "completed".equals(state); }
    private boolean isTerminalFailure(String state) { return "expired".equals(state) || "cancelled".equals(state) || "rejected".equals(state); }
    private CulqiOrderResponse orderResponse(Pago pago, OrdenClient.OrdenResponse orden) {
        return CulqiOrderResponse.builder().pagoId(pago.getId()).ordenId(pago.getOrdenId()).numeroOrden(orden.getNumeroOrden())
                .culqiOrderId(pago.getCulqiOrderId()).amount(pago.getAmount()).currency(pago.getCurrency())
                .status(pago.getExternalStatus()).expiresAt(pago.getCulqiOrderExpiresAt()).build();
    }

    @Transactional
    public CulqiChargeResponse procesarCargo(CulqiChargeRequest request) {
        log.info("Iniciando procesamiento de cargo con Culqi para orden ID: {}", request.getOrdenId());

        // 1. Validar la orden con orden-ms
        OrdenClient.OrdenResponse orden;
        try {
            orden = ordenClient.obtenerOrdenPorId(request.getOrdenId());
        } catch (Exception ex) {
            log.error("No se pudo obtener la orden {}: {}", request.getOrdenId(), ex.getMessage());
            throw new PaymentGatewayException("No se encontró la orden especificada para el pago.", HttpStatus.NOT_FOUND, "CULQI", true);
        }

        if (orden == null || orden.getTotal() == null || orden.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("La orden no tiene un monto válido para procesar el cobro.", HttpStatus.BAD_REQUEST, "CULQI", true);
        }

        if ("PAGADA".equalsIgnoreCase(orden.getEstado())) {
            throw new PaymentGatewayException("La orden #" + orden.getNumeroOrden() + " ya se encuentra pagada.", HttpStatus.CONFLICT, "CULQI", true);
        }

        // 2. Determinar email del cliente
        String email = request.getEmail();
        if (email == null || email.isBlank() || !email.contains("@")) {
            email = (orden.getClienteEmail() != null && orden.getClienteEmail().contains("@"))
                    ? orden.getClienteEmail()
                    : "cliente.medizano@gmail.com";
        }

        // 3. Monto en centavos (ej: 25.50 -> 2550)
        int amountInCents = orden.getTotal()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        // 4. Preparar payload para Culqi v2 Charges API
        Map<String, Object> chargePayload = new HashMap<>();
        chargePayload.put("amount", amountInCents);
        chargePayload.put("currency_code", culqiProperties.getCurrency());
        chargePayload.put("email", email);
        chargePayload.put("source_id", request.getToken());
        chargePayload.put("description", "Venta Farmacia MediZano - Orden " + orden.getNumeroOrden());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(culqiProperties.getSecretKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(chargePayload, headers);
        String url = culqiProperties.getBaseUrl() + "/charges";

        JsonNode culqiResponse;
        try {
            log.info("Llamando a Culqi Charges API: {} con monto: {} centavos", url, amountInCents);
            ResponseEntity<JsonNode> responseEntity = restTemplate.postForEntity(url, entity, JsonNode.class);
            culqiResponse = responseEntity.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Error devuelto por Culqi API (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            String errorMsg = "Error en pasarela Culqi al procesar la tarjeta.";
            try {
                JsonNode errJson = objectMapper.readTree(ex.getResponseBodyAsString());
                if (errJson.has("user_message")) {
                    errorMsg = errJson.get("user_message").asText();
                } else if (errJson.has("merchant_message")) {
                    errorMsg = errJson.get("merchant_message").asText();
                }
            } catch (Exception parseEx) {
                // Ignore parse error
            }
            throw new PaymentGatewayException(errorMsg, HttpStatus.valueOf(ex.getStatusCode().value()), "CULQI", true);
        } catch (Exception ex) {
            log.error("Fallo de comunicación con Culqi: {}", ex.getMessage());
            throw new PaymentGatewayException("No se pudo comunicar con la pasarela Culqi: " + ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, "CULQI", true);
        }

        if (culqiResponse == null || !culqiResponse.has("id")) {
            throw new PaymentGatewayException("Respuesta inesperada de la pasarela Culqi.", HttpStatus.INTERNAL_SERVER_ERROR, "CULQI", true);
        }

        String chargeId = culqiResponse.get("id").asText();
        String outcomeType = "aprobado";
        if (culqiResponse.has("outcome") && culqiResponse.get("outcome").has("type")) {
            outcomeType = culqiResponse.get("outcome").get("type").asText();
        }

        log.info("Cargo Culqi exitoso. Charge ID: {}, Estado: {}", chargeId, outcomeType);

        // 5. Guardar registro de Pago en DB local
        Pago pago = Pago.builder()
                .ordenId(orden.getId())
                .numeroOrden(orden.getNumeroOrden())
                .culqiChargeId(chargeId)
                .amount(orden.getTotal())
                .currency(culqiProperties.getCurrency())
                .status(Pago.EstadoPago.APPROVED)
                .provider("CULQI")
                .externalStatus(outcomeType)
                .paymentDate(LocalDateTime.now())
                .build();

        pago = pagoRepository.save(pago);

        // 6. Confirmar la orden en orden-ms
        boolean confirmed = confirmationService.confirmOrder(pago, "CULQI-" + chargeId);

        return CulqiChargeResponse.builder()
                .pagoId(pago.getId())
                .ordenId(orden.getId())
                .numeroOrden(orden.getNumeroOrden())
                .chargeId(chargeId)
                .amount(orden.getTotal())
                .currency(culqiProperties.getCurrency())
                .status(Pago.EstadoPago.APPROVED)
                .message("Pago con Culqi procesado exitosamente.")
                .orderConfirmed(confirmed)
                .build();
    }
}
