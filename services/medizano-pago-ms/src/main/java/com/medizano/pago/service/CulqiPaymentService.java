package com.medizano.pago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.CulqiProperties;
import com.medizano.pago.dto.*;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** Culqi DEVS integration. Amounts are always read from orden-ms, never supplied by the browser. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CulqiPaymentService {
    private static final BigDecimal QR_MINIMUM = new BigDecimal("6.00");
    private static final BigDecimal QR_MAXIMUM = new BigDecimal("500.00");
    private final PagoRepository pagoRepository;
    private final OrdenClient ordenClient;
    private final CulqiProperties properties;
    private final PaymentOrderConfirmationService confirmationService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    public CulqiConfigResponse publicConfig() {
        return CulqiConfigResponse.builder().publicKey(properties.getPublicKey()).currency("PEN").build();
    }

    @Transactional(readOnly = true)
    public List<PagoDTO> listByOrder(Long ordenId) {
        return pagoRepository.findByOrdenIdOrderByCreatedAtDesc(ordenId).stream().map(this::dto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagoDTO get(Long id) { return dto(pagoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Pago no encontrado"))); }

    @Transactional
    public CulqiOrderResponse createWalletOrder(CulqiOrderRequest request) {
        requireConfigured();
        OrdenClient.OrdenResponse order = requireOrder(request.getOrdenId(), "CULQI_QR");
        BigDecimal amount = order.getTotal();
        if (amount.compareTo(QR_MINIMUM) < 0 || amount.compareTo(QR_MAXIMUM) > 0)
            throw new IllegalArgumentException("Yape QR solo permite pagos entre S/ 6.00 y S/ 500.00.");
        Optional<Pago> existing = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(order.getId(), "CULQI_QR", Pago.EstadoPago.PENDING);
        if (existing.isPresent() && existing.get().getExpiresAt() != null && existing.get().getExpiresAt().isAfter(LocalDateTime.now())) return orderResponse(existing.get());

        LocalDateTime expiration = LocalDateTime.now().plusMinutes(properties.getQrExpirationMinutes());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", cents(amount));
        body.put("currency_code", "PEN");
        body.put("description", "MediZano pedido " + order.getNumeroOrden());
        body.put("order_number", order.getNumeroOrden());
        body.put("expiration_date", expiration.toInstant(ZoneOffset.UTC).getEpochSecond());
        body.put("confirm", true);
        body.put("metadata", Map.of("medizano_order_id", String.valueOf(order.getId()), "idempotency_key", UUID.randomUUID().toString()));
        if (order.getClienteEmail() != null && !order.getClienteEmail().isBlank()) body.put("client_details", Map.of("email", order.getClienteEmail()));
        JsonNode result = post("/orders", properties.getPublicKey(), body);
        String culqiOrderId = requiredText(result, "id");
        Pago pago = Pago.builder().ordenId(order.getId()).numeroOrden(order.getNumeroOrden()).amount(amount).currency("PEN")
                .provider("CULQI_QR").status(Pago.EstadoPago.PENDING).externalStatus(result.path("state").asText("pending"))
                .culqiOrderId(culqiOrderId).idempotencyKey(UUID.randomUUID().toString()).expiresAt(expiration).build();
        return orderResponse(pagoRepository.save(pago));
    }

    @Transactional
    public CulqiPaymentResponse chargeYape(CulqiChargeRequest request) {
        requireConfigured();
        OrdenClient.OrdenResponse order = requireOrder(request.getOrdenId(), "CULQI_YAPE");
        Optional<Pago> existingApproved = pagoRepository.findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(order.getId(), "CULQI_YAPE", Pago.EstadoPago.APPROVED);
        if (existingApproved.isPresent()) return paymentResponse(existingApproved.get());
        String idempotency = "medizano-yape-" + order.getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", cents(order.getTotal())); body.put("currency_code", "PEN"); body.put("source_id", request.getTokenId());
        body.put("email", order.getClienteEmail() == null || order.getClienteEmail().isBlank() ? "cliente@medizano.test" : order.getClienteEmail());
        body.put("description", "MediZano pedido " + order.getNumeroOrden()); body.put("metadata", Map.of("medizano_order_id", String.valueOf(order.getId())));
        JsonNode result = post("/charges", properties.getSecretKey(), body, idempotency);
        String chargeId = requiredText(result, "id"); String status = result.path("outcome").path("type").asText(result.path("status").asText("pending"));
        boolean isApproved = "venta_exitosa".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status);
        Pago pago = Pago.builder().ordenId(order.getId()).numeroOrden(order.getNumeroOrden()).amount(order.getTotal()).currency("PEN")
                .provider("CULQI_YAPE").status(isApproved ? Pago.EstadoPago.APPROVED : Pago.EstadoPago.REJECTED).externalStatus(status)
                .culqiChargeId(chargeId).idempotencyKey(idempotency).paymentDate(LocalDateTime.now()).build();
        pago = pagoRepository.save(pago);
        if (isApproved) confirmationService.confirmOrder(pago, confirmationService.buildReference(pago));
        return paymentResponse(pago);
    }

    /** Webhooks are idempotent: repeated Culqi deliveries update one payment reference only. */
    @Transactional
    public void processWebhook(Map<String, Object> event) {
        JsonNode root = objectMapper.valueToTree(event); JsonNode data = root.path("data");
        String orderId = data.path("id").asText(""); if (orderId.isBlank()) return;
        Pago pago = pagoRepository.findByCulqiOrderId(orderId).orElseThrow(() -> new IllegalArgumentException("Orden Culqi desconocida"));
        String state = data.path("state").asText(root.path("type").asText("pending"));
        pago.setExternalStatus(state);
        if (state.equalsIgnoreCase("paid") || state.equalsIgnoreCase("approved")) { pago.setStatus(Pago.EstadoPago.APPROVED); pago.setPaymentDate(LocalDateTime.now()); confirmationService.confirmOrder(pago, confirmationService.buildReference(pago)); }
        else if (state.equalsIgnoreCase("expired")) pago.setStatus(Pago.EstadoPago.CANCELLED);
        else if (state.equalsIgnoreCase("rejected")) pago.setStatus(Pago.EstadoPago.REJECTED);
        pagoRepository.save(pago);
    }

    private OrdenClient.OrdenResponse requireOrder(Long id, String method) {
        OrdenClient.OrdenResponse order = ordenClient.obtenerOrdenPorId(id);
        if (order == null) throw new IllegalArgumentException("Orden no encontrada");
        if ("PAGADA".equalsIgnoreCase(order.getEstado())) throw new IllegalStateException("La orden ya fue pagada");
        if (!method.equalsIgnoreCase(order.getMetodoPago())) throw new IllegalStateException("La orden no corresponde al método " + method);
        return order;
    }
    private void requireConfigured() { if (properties.getPublicKey() == null || !properties.getPublicKey().startsWith("pk_test_") || properties.getSecretKey() == null || !properties.getSecretKey().startsWith("sk_test_")) throw new IllegalStateException("Culqi DEVS no está configurado con llaves test."); }
    private JsonNode post(String path, String key, Object body) { return post(path, key, body, null); }
    private JsonNode post(String path, String key, Object body, String idempotency) {
        String url = properties.getApiBaseUrl().replaceAll("/+$", "") + path;
        try { String raw = restClient.post().uri(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + key).header("x-culqi-product", "online").header("x-idempotency-key", idempotency == null ? UUID.randomUUID().toString() : idempotency).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class); return objectMapper.readTree(raw); }
        catch (Exception e) { log.warn("Culqi request failed: {}", e.getMessage()); throw new IllegalStateException("Culqi no pudo procesar el pago. Inténtalo nuevamente."); }
    }
    private int cents(BigDecimal amount) { return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact(); }
    private String requiredText(JsonNode node, String field) { String value = node.path(field).asText(); if (value.isBlank()) throw new IllegalStateException("Respuesta inválida de Culqi"); return value; }
    private CulqiOrderResponse orderResponse(Pago p) { return CulqiOrderResponse.builder().pagoId(p.getId()).ordenId(p.getOrdenId()).numeroOrden(p.getNumeroOrden()).culqiOrderId(p.getCulqiOrderId()).status(p.getExternalStatus()).amount(p.getAmount()).currency(p.getCurrency()).expiresAt(p.getExpiresAt()).build(); }
    private CulqiPaymentResponse paymentResponse(Pago p) { return CulqiPaymentResponse.builder().pagoId(p.getId()).ordenId(p.getOrdenId()).numeroOrden(p.getNumeroOrden()).transactionId(p.getCulqiChargeId()).status(p.getStatus().name()).amount(p.getAmount()).currency(p.getCurrency()).timestamp(p.getPaymentDate()).orderConfirmed(p.getOrderConfirmed()).build(); }
    private PagoDTO dto(Pago p) { return PagoDTO.builder().id(p.getId()).ordenId(p.getOrdenId()).numeroOrden(p.getNumeroOrden()).culqiOrderId(p.getCulqiOrderId()).culqiChargeId(p.getCulqiChargeId()).amount(p.getAmount()).currency(p.getCurrency()).status(p.getStatus()).provider(p.getProvider()).externalStatus(p.getExternalStatus()).orderConfirmed(p.getOrderConfirmed()).confirmationAttempts(p.getConfirmationAttempts()).lastConfirmationError(p.getLastConfirmationError()).paymentDate(p.getPaymentDate()).createdAt(p.getCreatedAt()).build(); }
}
