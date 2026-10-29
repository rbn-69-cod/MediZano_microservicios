package com.medizano.proveedor.controller;

import com.medizano.proveedor.dto.CompraRequest;
import com.medizano.proveedor.dto.CompraResponse;
import com.medizano.proveedor.dto.RecepcionCompraRequest;
import com.medizano.proveedor.entity.Compra.EstadoCompra;
import com.medizano.proveedor.service.CompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/compras")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Gestión de órdenes de compra a proveedores y recepción física a inventario")
public class CompraController {

    private final CompraService compraService;

    @PostMapping
    @Operation(summary = "Crear una nueva orden de compra a proveedor")
    public ResponseEntity<CompraResponse> crearCompra(
            @Valid @RequestBody CompraRequest request,
            @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        if (authUser != null && (request.getUsuarioRegistro() == null || request.getUsuarioRegistro().isBlank())) {
            request.setUsuarioRegistro(authUser);
        }
        CompraResponse creada = compraService.crearCompra(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping
    @Operation(summary = "Listar compras con filtros por proveedor, estado, fechas y paginación")
    public ResponseEntity<Page<CompraResponse>> listarCompras(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(compraService.listarCompras(proveedorId, estado, fechaInicio, fechaFin, search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle de una compra por ID")
    public ResponseEntity<CompraResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(compraService.obtenerPorId(id));
    }

    @PostMapping("/{id}/recepcion")
    @Operation(summary = "Confirmar recepción física de mercadería e ingresar a lotes/kardex en inventario")
    public ResponseEntity<CompraResponse> confirmarRecepcion(
            @PathVariable Long id,
            @Valid @RequestBody RecepcionCompraRequest request,
            @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        if (authUser != null && (request.getUsuarioRecepcion() == null || request.getUsuarioRecepcion().isBlank())) {
            request.setUsuarioRecepcion(authUser);
        }
        return ResponseEntity.ok(compraService.confirmarRecepcion(id, request));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualizar estado en la línea de tracking de compra")
    public ResponseEntity<CompraResponse> actualizarEstado(
            @PathVariable Long id,
            @RequestParam EstadoCompra nuevoEstado,
            @RequestParam(required = false) String observaciones) {
        return ResponseEntity.ok(compraService.actualizarEstado(id, nuevoEstado, observaciones));
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una orden de compra pendiente")
    public ResponseEntity<CompraResponse> cancelarCompra(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(compraService.cancelarCompra(id, motivo));
    }
}

