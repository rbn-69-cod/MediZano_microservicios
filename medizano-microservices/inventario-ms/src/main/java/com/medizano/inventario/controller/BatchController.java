package com.medizano.inventario.controller;

import com.medizano.inventario.dto.*;
import com.medizano.inventario.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacist/batches")
@RequiredArgsConstructor
@Tag(name = "Lotes e Inventario", description = "Gestión de Lotes y Stock Farmacéutico")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @Operation(summary = "Crear nuevo lote")
    public ResponseEntity<BatchResponse> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createBatch(request));
    }

    @GetMapping("/medicine/{medicineId}")
    @Operation(summary = "Obtener lotes de un medicamento")
    public ResponseEntity<List<BatchResponse>> getBatchesByMedicine(@PathVariable Long medicineId) {
        return ResponseEntity.ok(batchService.getBatchesByMedicine(medicineId));
    }

    @GetMapping("/expired")
    @Operation(summary = "Listar lotes vencidos")
    public ResponseEntity<List<BatchResponse>> getExpiredBatches() {
        return ResponseEntity.ok(batchService.getExpiredBatches());
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Listar lotes con bajo stock")
    public ResponseEntity<List<BatchResponse>> getLowStockBatches(@RequestParam(defaultValue = "10") Integer threshold) {
        return ResponseEntity.ok(batchService.getLowStockBatches(threshold));
    }

    @GetMapping
    @Operation(summary = "Listar todos los lotes")
    public ResponseEntity<List<BatchResponse>> getAllBatches() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Obtener lote por código de barras de unidad")
    public ResponseEntity<BatchResponse> getBatchByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(batchService.getBatchByBarcode(barcode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener lote por ID")
    public ResponseEntity<BatchResponse> getBatchById(@PathVariable Long id) {
        return ResponseEntity.ok(batchService.getBatchById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar lote")
    public ResponseEntity<BatchResponse> updateBatch(@PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request) {
        return ResponseEntity.ok(batchService.updateBatch(id, request));
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "Actualizar cantidad de stock")
    public ResponseEntity<BatchResponse> updateStock(@PathVariable Long id, @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(batchService.updateStock(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar lote")
    public ResponseEntity<Void> deleteBatch(@PathVariable Long id) {
        batchService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/barcodes")
    @Operation(summary = "Listar códigos de barra del lote")
    public ResponseEntity<List<StockBarcodeResponse>> getBarcodesByBatch(@PathVariable Long id) {
        return ResponseEntity.ok(batchService.getBarcodesByBatchId(id));
    }

    @PostMapping("/{id}/barcodes")
    @Operation(summary = "Agregar códigos de barra al lote")
    public ResponseEntity<List<StockBarcodeResponse>> addBarcodesToBatch(@PathVariable Long id, @Valid @RequestBody AddBarcodesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.addBarcodesToBatch(id, request));
    }

    @DeleteMapping("/{id}/barcodes")
    @Operation(summary = "Eliminar códigos de barra del lote")
    public ResponseEntity<Void> deleteBarcodesFromBatch(@PathVariable Long id, @RequestParam("barcodeIds") List<Long> barcodeIds) {
        batchService.deleteBarcodesFromBatch(id, barcodeIds);
        return ResponseEntity.noContent().build();
    }
}

