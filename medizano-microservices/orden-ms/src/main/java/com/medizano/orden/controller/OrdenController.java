package com.medizano.orden.controller;

import com.medizano.orden.dto.CrearOrdenRequest;
import com.medizano.orden.dto.OrdenDTO;
import com.medizano.orden.service.OrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@Tag(name = "Órdenes", description = "Microservicio Transaccional de Órdenes y Pedidos (Kenyi)")
public class OrdenController {

    private final OrdenService ordenService;

    @GetMapping
    @Operation(summary = "Listar órdenes recientes")
    public ResponseEntity<List<OrdenDTO>> listar() {
        return ResponseEntity.ok(ordenService.listarOrdenes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener orden por ID")
    public ResponseEntity<OrdenDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordenService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nueva orden (Efectivo o PayPal)")
    public ResponseEntity<OrdenDTO> crear(@Valid @RequestBody CrearOrdenRequest request) {
        OrdenDTO orden = ordenService.crearOrden(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(orden);
    }

    @PostMapping("/{id}/confirmar-pago")
    @Operation(summary = "Confirmar pago de orden tras captura de PayPal (descuenta inventario)")
    public ResponseEntity<OrdenDTO> confirmarPago(
            @PathVariable Long id,
            @RequestParam(value = "referenciaPago", required = false) String referenciaPago) {
        return ResponseEntity.ok(ordenService.confirmarPagoOrden(id, referenciaPago != null ? referenciaPago : "PAYPAL-CAPTURE"));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar orden")
    public ResponseEntity<OrdenDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(ordenService.cancelarOrden(id));
    }
}

