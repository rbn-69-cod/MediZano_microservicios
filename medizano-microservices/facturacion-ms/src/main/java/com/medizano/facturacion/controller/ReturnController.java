package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.ReturnRequest;
import com.medizano.facturacion.dto.ReturnResponse;
import com.medizano.facturacion.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Devoluciones POS", description = "Gestión de devoluciones y notas de crédito")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    @Operation(summary = "Procesar devolución de venta")
    public ResponseEntity<ReturnResponse> processReturn(@Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.processReturn(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener devolución por ID")
    public ResponseEntity<ReturnResponse> getReturnById(@PathVariable Long id) {
        return ResponseEntity.ok(returnService.getReturnById(id));
    }

    @GetMapping("/bill/{billId}")
    @Operation(summary = "Obtener devoluciones por ID de venta")
    public ResponseEntity<List<ReturnResponse>> getReturnsByBillId(@PathVariable Long billId) {
        return ResponseEntity.ok(returnService.getReturnsByBillId(billId));
    }
}

