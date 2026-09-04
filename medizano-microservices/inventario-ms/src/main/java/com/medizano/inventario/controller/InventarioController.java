package com.medizano.inventario.controller;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.service.InventarioService;
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

@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@Tag(name = "Kardex e Inventario v1", description = "Control transaccional de stock, entradas, salidas, kardex y descuento automático por venta")
public class InventarioController {

    private final InventarioService inventarioService;

    @GetMapping
    @Operation(summary = "Listar estado de inventario de todos los productos", description = "Devuelve el estado consolidado de existencias, stock disponible y almacén para todos los productos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventario listado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<InventarioDTO>> listar() {
        return ResponseEntity.ok(inventarioService.listarInventario());
    }

    @GetMapping("/{productoId}")
    @Operation(summary = "Obtener stock de un producto por ID", description = "Consulta la cantidad actual disponible y ubicación en almacén de un producto determinado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro de inventario encontrado", content = @Content(schema = @Schema(implementation = InventarioDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "No existe registro de inventario para el ID de producto especificado")
    })
    public ResponseEntity<InventarioDTO> obtenerPorProductoId(
            @Parameter(description = "ID del producto a consultar", example = "1", required = true)
            @PathVariable Long productoId) {
        return ResponseEntity.ok(inventarioService.obtenerPorProductoId(productoId));
    }

    @PostMapping("/entrada")
    @Operation(summary = "Registrar entrada/ingreso de stock", description = "Registra un ingreso de existencias por abastecimiento o compra a proveedor, actualizando el saldo en kardex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada procesada y stock incrementado exitosamente", content = @Content(schema = @Schema(implementation = InventarioDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (ej. cantidad menor o igual a cero)"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<InventarioDTO> registrarEntrada(@Valid @RequestBody MovimientoRequest request) {
        return ResponseEntity.ok(inventarioService.registrarEntrada(request));
    }

    @PostMapping("/salida")
    @Operation(summary = "Registrar salida de stock", description = "Registra una disminución manual de existencias por concepto de merma, vencimiento o rotura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Salida procesada y stock descontado exitosamente", content = @Content(schema = @Schema(implementation = InventarioDTO.class))),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida o superior al stock disponible"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<InventarioDTO> registrarSalida(@Valid @RequestBody MovimientoRequest request) {
        return ResponseEntity.ok(inventarioService.registrarSalida(request));
    }

    @PostMapping("/descontar-venta")
    @Operation(summary = "Descontar stock tras confirmación de venta pagada", description = "Endpoint interno llamado por el microservicio de órdenes para descontar automáticamente las unidades vendidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock descontado exitosamente por venta"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida o stock insuficiente para concretar la venta"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado en inventario")
    })
    public ResponseEntity<Void> descontarStockVenta(@Valid @RequestBody DescuentoStockRequest request) {
        inventarioService.descontarStockVenta(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Consultar historial de movimientos (Kardex)", description = "Obtiene la bitácora histórica de transacciones (entradas, salidas, ventas) con opción de filtro por producto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de movimientos recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<MovimientoDTO>> listarMovimientos(
            @Parameter(description = "ID opcional del producto para filtrar movimientos específicos", example = "1")
            @RequestParam(value = "productoId", required = false) Long productoId) {
        return ResponseEntity.ok(inventarioService.listarMovimientos(productoId));
    }
}

