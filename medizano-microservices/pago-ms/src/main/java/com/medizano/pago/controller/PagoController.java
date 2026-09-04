package com.medizano.pago.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.medizano.pago.dto.*;
import com.medizano.pago.service.MercadoPagoPaymentService;
import com.medizano.pago.service.PayPalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Pasarelas de Pago", description = "Procesamiento integral de transacciones con PayPal Orders v2 Sandbox y Mercado Pago Checkout Pro con Webhooks HMAC")
public class PagoController {

    private final PayPalService payPalService;
    private final MercadoPagoPaymentService mercadoPagoService;

    @GetMapping({"/config", "/mercadopago/config", "/paypal/config"})
    @Operation(summary = "Obtener configuración pública de pasarelas", description = "Devuelve el Client ID de PayPal y la Public Key de Mercado Pago requeridos por los SDKs del frontend.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración pública recuperada", content = @Content(schema = @Schema(implementation = PaymentConfigResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
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
    @Operation(summary = "Listar pagos asociados a una orden", description = "Obtiene el historial de pagos y transacciones registradas para una orden específica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de transacciones asociadas a la orden"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<PagoDTO>> listarPorOrden(
            @Parameter(description = "ID de la orden de venta POS", example = "1", required = true)
            @PathVariable Long ordenId) {
        return ResponseEntity.ok(payPalService.listarPorOrden(ordenId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de pago por ID", description = "Consulta la información de una transacción de pago registrada en la base de datos por su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transacción de pago encontrada", content = @Content(schema = @Schema(implementation = PagoDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Transacción de pago no encontrada")
    })
    public ResponseEntity<PagoDTO> obtenerPorId(
            @Parameter(description = "ID de la transacción de pago", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(payPalService.obtenerPorId(id));
    }

    // ==================== PAYPAL SANDBOX ====================

    @PostMapping({"/paypal/order", "/paypal/create-order"})
    @Operation(summary = "Crear orden de pago en PayPal Sandbox Orders API v2", description = "Crea una orden de pago en PayPal con intención CAPTURE, retornando el orderId y enlaces HATEOAS para aprobación del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de PayPal creada exitosamente", content = @Content(schema = @Schema(implementation = PayPalOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de orden inválidos o monto <= 0"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "502", description = "Error de comunicación con PayPal Sandbox")
    })
    public ResponseEntity<PayPalOrderResponse> crearOrdenPayPal(@Valid @RequestBody PayPalOrderRequest request) {
        return ResponseEntity.ok(payPalService.crearOrden(request));
    }

    @PostMapping("/paypal/capture/{paypalOrderId}")
    @Operation(summary = "Capturar pago de orden autorizada en PayPal Sandbox", description = "Captura los fondos de una orden autorizada por el cliente en PayPal y confirma la transacción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago capturado exitosamente", content = @Content(schema = @Schema(implementation = PayPalCaptureResponse.class))),
            @ApiResponse(responseCode = "400", description = "La orden no se encuentra en estado para captura"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada en PayPal"),
            @ApiResponse(responseCode = "502", description = "Fallo en la llamada a la API de captura de PayPal")
    })
    public ResponseEntity<PayPalCaptureResponse> capturarOrdenPayPal(
            @Parameter(description = "ID de la orden emitido por PayPal (ej. 5O190127TN364715T)", example = "5O190127TN364715T", required = true)
            @PathVariable String paypalOrderId) {
        return ResponseEntity.ok(payPalService.capturarOrden(paypalOrderId));
    }

    @GetMapping("/paypal/order/{paypalOrderId}")
    @Operation(summary = "Consultar estado de la orden directamente en PayPal Sandbox", description = "Consulta en vivo contra la API de PayPal el estado y detalles completos de la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de la orden recuperado"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada en PayPal"),
            @ApiResponse(responseCode = "502", description = "Error al consultar PayPal")
    })
    public ResponseEntity<JsonNode> consultarOrdenPayPal(
            @Parameter(description = "ID de la orden en PayPal", example = "5O190127TN364715T", required = true)
            @PathVariable String paypalOrderId) {
        return ResponseEntity.ok(payPalService.consultarOrdenPayPal(paypalOrderId));
    }

    // ==================== MERCADO PAGO ====================

    @PostMapping("/mercadopago/preference")
    @Operation(summary = "Crear preferencia en Mercado Pago Checkout Pro", description = "Genera una preferencia de pago en Mercado Pago Checkout Pro devolviendo el preferenceId y la URL de redirección init_point.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencia generada exitosamente", content = @Content(schema = @Schema(implementation = MercadoPagoPreferenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de preferencia inválidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<MercadoPagoPreferenceResponse> crearPreferenciaMercadoPago(@Valid @RequestBody MercadoPagoPreferenceRequest request) {
        return ResponseEntity.ok(mercadoPagoService.crearPreferencia(request));
    }

    @PostMapping("/mercadopago/verify")
    @Operation(summary = "Verificar y confirmar pago en Mercado Pago mediante backend", description = "Consulta directamente a Mercado Pago el estado del pago por su payment_id para garantizar la acreditación real de fondos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago verificado y registrado", content = @Content(schema = @Schema(implementation = MercadoPagoPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID de pago inválido o pago rechazado"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<MercadoPagoPaymentResponse> verificarPagoMercadoPago(@Valid @RequestBody MercadoPagoVerifyRequest request) {
        return ResponseEntity.ok(mercadoPagoService.verificarYConfirmarPago(request));
    }

    @PostMapping("/mercadopago/webhook")
    @Operation(summary = "Recibir notificaciones Webhook de Mercado Pago con validación HMAC", description = "Endpoint asíncrono para notificaciones IPN. Verifica la firma x-signature con clave secreta HMAC para garantizar la autenticidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación webhook procesada correctamente"),
            @ApiResponse(responseCode = "401", description = "Firma HMAC inválida o cabecera x-signature ausente"),
            @ApiResponse(responseCode = "500", description = "Error interno procesando el webhook")
    })
    public ResponseEntity<?> webhookMercadoPago(
            @RequestBody Map<String, Object> payload,
            @Parameter(description = "Firma criptográfica HMAC-SHA256 enviada por Mercado Pago", example = "ts=1709500000,v1=a1b2c3d4e5f6...")
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @Parameter(description = "Identificador único de la petición HTTP del webhook", example = "req-12345")
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
