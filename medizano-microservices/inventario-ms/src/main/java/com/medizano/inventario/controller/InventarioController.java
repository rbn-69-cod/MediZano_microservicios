package com.medizano.inventario.controller;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@Tag(name = "Inventario", description = "Microservicio Transaccional de Inventario y Stock (Rubén)")
public class InventarioController {

    private final InventarioService inventarioService;

    @GetMapping
    @Operation(summary = "Listar estado de inventario de todos los productos")
    public ResponseEntity<List<InventarioDTO>> listar() {
        return ResponseEntity.ok(inventarioService.listarInventario());
    }

    @GetMapping("/{productoId}")
    @Operation(summary = "Obtener stock de un producto por ID")
    public ResponseEntity<InventarioDTO> obtenerPorProductoId(@PathVariable Long productoId) {
        return ResponseEntity.ok(inventarioService.obtenerPorProductoId(productoId));
    }

    @PostMapping("/entrada")
    @Operation(summary = "Registrar entrada/ingreso de stock")
    public ResponseEntity<InventarioDTO> registrarEntrada(@Valid @RequestBody MovimientoRequest request) {
        return ResponseEntity.ok(inventarioService.registrarEntrada(request));
    }

    @PostMapping("/salida")
    @Operation(summary = "Registrar salida de stock")
    public ResponseEntity<InventarioDTO> registrarSalida(@Valid @RequestBody MovimientoRequest request) {
        return ResponseEntity.ok(inventarioService.registrarSalida(request));
    }

    @PostMapping("/descontar-venta")
    @Operation(summary = "Descontar stock tras confirmación de venta pagada")
    public ResponseEntity<Void> descontarStockVenta(@Valid @RequestBody DescuentoStockRequest request) {
        inventarioService.descontarStockVenta(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Consultar historial de movimientos")
    public ResponseEntity<List<MovimientoDTO>> listarMovimientos(@RequestParam(value = "productoId", required = false) Long productoId) {
        return ResponseEntity.ok(inventarioService.listarMovimientos(productoId));
    }
}

