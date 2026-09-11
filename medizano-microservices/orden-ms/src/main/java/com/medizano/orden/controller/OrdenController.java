package com.medizano.orden.controller;

import com.medizano.orden.dto.CrearOrdenRequest;
import com.medizano.orden.dto.OrdenDTO;
import com.medizano.orden.service.OrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@Tag(name = "Órdenes de Venta", description = "Gestión del ciclo de vida de órdenes POS: creación, asociación con clientes, ítems, cálculo de total y confirmación de pago")
public class OrdenController {

    private final OrdenService ordenService;

    @Value("${security.internal-service-token}")
    private String internalServiceToken;

    @GetMapping
    @Operation(summary = "Listar órdenes recientes", description = "Obtiene las órdenes de venta registradas en el sistema POS ordenadas cronológicamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de órdenes recuperada exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<OrdenDTO>> listar() {
        return ResponseEntity.ok(ordenService.listarOrdenes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener orden por ID", description = "Recupera la cabecera y el detalle completo de ítems de una orden de venta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden encontrada", content = @Content(schema = @Schema(implementation = OrdenDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada con el ID provisto")
    })
    public ResponseEntity<OrdenDTO> obtenerPorId(
            @Parameter(description = "ID único de la orden", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(ordenService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nueva orden (Efectivo o PayPal)", description = "Registra una orden de venta en estado PENDIENTE con validación de existencia de cliente e ítems.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada exitosamente en estado PENDIENTE", content = @Content(schema = @Schema(implementation = OrdenDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de orden inválidos (ej. ítems vacíos o cantidades menores a 1)"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Cliente o producto no encontrado")
    })
    public ResponseEntity<OrdenDTO> crear(@Valid @RequestBody CrearOrdenRequest request,
                                          @RequestHeader(value = "X-Auth-User", required = false) String authUser,
                                          @RequestHeader(value = "X-Auth-User-Id", required = false) Long authUserId) {
        if (authUser != null && !authUser.isBlank()) {
            request.setUsuarioNombre(authUser);
            request.setUsuarioId(authUserId);
        }
        OrdenDTO orden = ordenService.crearOrden(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(orden);
    }

    @PostMapping("/{id}/confirmar-pago")
    @Operation(summary = "Confirmar pago de orden tras captura de PayPal (descuenta inventario)", description = "Transiciona la orden a PAGADA tras validación de fondos y emite la llamada feign a inventario para descuento de stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden confirmada y pagada exitosamente", content = @Content(schema = @Schema(implementation = OrdenDTO.class))),
            @ApiResponse(responseCode = "400", description = "La orden ya se encontraba pagada o cancelada"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflicto por stock insuficiente al intentar descontar inventario")
    })
    public ResponseEntity<OrdenDTO> confirmarPago(
            @Parameter(description = "ID de la orden a confirmar", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Código o identificador de referencia del pago (ej. PayPal Order ID)", example = "PAYPAL-5O190127TN364715T")
            @RequestParam(value = "referenciaPago") String referenciaPago,
            @RequestHeader(value = "X-Internal-Service-Token") String providedToken) {
        if (!MessageDigest.isEqual(internalServiceToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ordenService.confirmarPagoOrden(id, referenciaPago));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar orden", description = "Cancela una orden de venta que no haya completado el proceso de pago.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden cancelada exitosamente", content = @Content(schema = @Schema(implementation = OrdenDTO.class))),
            @ApiResponse(responseCode = "400", description = "No se puede cancelar una orden que ya fue pagada"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada")
    })
    public ResponseEntity<OrdenDTO> cancelar(
            @Parameter(description = "ID de la orden a cancelar", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(ordenService.cancelarOrden(id));
    }
}
