package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.ReturnRequest;
import com.medizano.facturacion.dto.ReturnResponse;
import com.medizano.facturacion.service.ReturnService;
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

import java.util.List;

@RestController
@RequestMapping("/api/cashier/returns")
@RequiredArgsConstructor
@Tag(name = "Devoluciones POS", description = "Gestión de devoluciones de productos, notas de crédito y reversión de transacciones POS")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    @Operation(summary = "Procesar devolución de venta", description = "Registra la devolución de uno o más ítems de una venta indicando la cantidad, motivo y calculando el importe a reintegrar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Devolución procesada exitosamente", content = @Content(schema = @Schema(implementation = ReturnResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de devolución inválidos (ej. cantidad supera lo facturado)"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol CASHIER o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Factura o ítem original no encontrado")
    })
    public ResponseEntity<ReturnResponse> processReturn(@Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.processReturn(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener devolución por ID", description = "Recupera los detalles de una nota de devolución procesada según su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devolución encontrada", content = @Content(schema = @Schema(implementation = ReturnResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Devolución no encontrada")
    })
    public ResponseEntity<ReturnResponse> getReturnById(
            @Parameter(description = "ID de la devolución", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(returnService.getReturnById(id));
    }

    @GetMapping("/bill/{billId}")
    @Operation(summary = "Obtener devoluciones por ID de venta", description = "Lista todas las devoluciones emitidas contra una factura o comprobante específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de devoluciones asociadas a la venta"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<ReturnResponse>> getReturnsByBillId(
            @Parameter(description = "ID de la factura original", example = "1", required = true)
            @PathVariable Long billId) {
        return ResponseEntity.ok(returnService.getReturnsByBillId(billId));
    }
}

