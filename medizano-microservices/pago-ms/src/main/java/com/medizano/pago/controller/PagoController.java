package com.medizano.pago.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.medizano.pago.dto.*;
import com.medizano.pago.service.MercadoPagoPaymentService;
import com.medizano.pago.service.PayPalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Microservicio Transaccional de Pagos: PayPal Sandbox y Mercado Pago (Rubén)")
public class PagoController {

    private final PayPalService payPalService;
    private final MercadoPagoPaymentService mercadoPagoService;

    @GetMapping({"/config", "/mercadopago/config", "/paypal/config"})
    @Operation(summary = "Obtener configuración pública de pasarelas (PayPal Client ID y Mercado Pago Public Key)")
    public ResponseEntity<PaymentConfigResponse> obtenerConfiguracion() {
        PayPalConfigResponse payPalConfig = payPalService.obtenerConfiguracionPublica();
        PaymentConfigResponse config = PaymentConfigResponse.builder()
                .payPalClientId(payPalConfig.getClientId())
                .payPalCurrency(payPalConfig.getCurrency())
                .payPalBaseUrl(payPalConfig.getBaseUrl())
                .mpPublicKey(mercadoPagoService.getProperties().getPublicKey())
                .mpCurrency(mercadoPagoService.getProperties().getCurrency())
                .build();
        return ResponseEntity.ok(config);
    }

    @GetMapping("/orden/{ordenId}")
    @Operation(summary = "Listar pagos asociados a una orden")
    public ResponseEntity<List<PagoDTO>> listarPorOrden(@PathVariable Long ordenId) {
        return ResponseEntity.ok(payPalService.listarPorOrden(ordenId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de pago por ID")
    public ResponseEntity<PagoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(payPalService.obtenerPorId(id));
    }

    // ==================== PAYPAL SANDBOX ====================

    @PostMapping({"/paypal/order", "/paypal/create-order"})
    @Operation(summary = "Crear orden de pago en PayPal Sandbox Orders API v2")
    public ResponseEntity<PayPalOrderResponse> crearOrdenPayPal(@Valid @RequestBody PayPalOrderRequest request) {
        return ResponseEntity.ok(payPalService.crearOrden(request));
    }

    @PostMapping("/paypal/capture/{paypalOrderId}")
    @Operation(summary = "Capturar pago de orden autorizada en PayPal Sandbox")
    public ResponseEntity<PayPalCaptureResponse> capturarOrdenPayPal(@PathVariable String paypalOrderId) {
        return ResponseEntity.ok(payPalService.capturarOrden(paypalOrderId));
    }

    @GetMapping("/paypal/order/{paypalOrderId}")
    @Operation(summary = "Consultar estado de la orden directamente en PayPal Sandbox")
    public ResponseEntity<JsonNode> consultarOrdenPayPal(@PathVariable String paypalOrderId) {
        return ResponseEntity.ok(payPalService.consultarOrdenPayPal(paypalOrderId));
    }

    // ==================== MERCADO PAGO ====================

    @PostMapping("/mercadopago/preference")
    @Operation(summary = "Crear preferencia en Mercado Pago Checkout Pro")
    public ResponseEntity<MercadoPagoPreferenceResponse> crearPreferenciaMercadoPago(@Valid @RequestBody MercadoPagoPreferenceRequest request) {
        return ResponseEntity.ok(mercadoPagoService.crearPreferencia(request));
    }

    @PostMapping("/mercadopago/verify")
    @Operation(summary = "Verificar y confirmar pago en Mercado Pago mediante backend")
    public ResponseEntity<MercadoPagoPaymentResponse> verificarPagoMercadoPago(@Valid @RequestBody MercadoPagoVerifyRequest request) {
        return ResponseEntity.ok(mercadoPagoService.verificarYConfirmarPago(request));
    }

    @PostMapping("/mercadopago/webhook")
    @Operation(summary = "Recibir notificaciones Webhook de Mercado Pago con validación HMAC")
    public ResponseEntity<?> webhookMercadoPago(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId) {
        try {
            mercadoPagoService.procesarWebhook(payload, xSignature, xRequestId);
            return ResponseEntity.ok().build();
        } catch (SecurityException ex) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", ex.getMessage()));
        }
    }
}
