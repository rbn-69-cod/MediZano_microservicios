package com.medizano.inventario.controller;

import com.medizano.inventario.dto.*;
import com.medizano.inventario.service.BatchService;
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
@RequestMapping("/api/pharmacist/batches")
@RequiredArgsConstructor
@Tag(name = "Lotes e Inventario", description = "Gestión de lotes farmacéuticos, fechas de caducidad, códigos de barra por unidad y alertas de stock bajo")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @Operation(summary = "Crear nuevo lote", description = "Registra un nuevo lote de medicamentos especificando número de lote, fecha de caducidad, stock inicial y precios.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lote registrado exitosamente", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de lote inválidos (ej. fecha de caducidad pasada o stock negativo)"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol PHARMACIST o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado para asociar el lote")
    })
    public ResponseEntity<BatchResponse> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createBatch(request));
    }

    @GetMapping("/medicine/{medicineId}")
    @Operation(summary = "Obtener lotes de un medicamento", description = "Lista los lotes existentes de un medicamento ordenados cronológicamente por fecha de vencimiento (estrategia FEFO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lotes encontrados para el medicamento"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<List<BatchResponse>> getBatchesByMedicine(
            @Parameter(description = "ID del medicamento", example = "1", required = true)
            @PathVariable Long medicineId) {
        return ResponseEntity.ok(batchService.getBatchesByMedicine(medicineId));
    }

    @GetMapping("/expired")
    @Operation(summary = "Listar lotes vencidos", description = "Obtiene todos los lotes cuya fecha de caducidad es anterior o igual al día actual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de lotes caducados"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<BatchResponse>> getExpiredBatches() {
        return ResponseEntity.ok(batchService.getExpiredBatches());
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Listar lotes con bajo stock", description = "Consulta los lotes cuyo stock disponible se encuentra en o por debajo del umbral indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de lotes con inventario crítico"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<BatchResponse>> getLowStockBatches(
            @Parameter(description = "Umbral límite de existencias (por defecto 10 unidades)", example = "10")
            @RequestParam(defaultValue = "10") Integer threshold) {
        return ResponseEntity.ok(batchService.getLowStockBatches(threshold));
    }

    @GetMapping
    @Operation(summary = "Listar todos los lotes", description = "Devuelve la totalidad de lotes registrados en el inventario con sus cantidades y vencimientos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado completo de lotes"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<BatchResponse>> getAllBatches() {
        return ResponseEntity.ok(batchService.getAllBatches());
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Obtener lote por código de barras de unidad", description = "Localiza el lote correspondiente a una unidad física escaneada mediante su código de barras individual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lote encontrado", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Código de barras no registrado en ningún lote")
    })
    public ResponseEntity<BatchResponse> getBatchByBarcode(
            @Parameter(description = "Código de barras de la unidad", example = "7751234567890-001", required = true)
            @PathVariable String barcode) {
        return ResponseEntity.ok(batchService.getBatchByBarcode(barcode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener lote por ID", description = "Retorna el detalle completo de un lote por su identificador primario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lote encontrado", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<BatchResponse> getBatchById(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(batchService.getBatchById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar lote", description = "Modifica los atributos del lote tales como fecha de caducidad, precios o notas del proveedor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lote actualizado exitosamente", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de actualización inválidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol PHARMACIST o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<BatchResponse> updateBatch(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateBatchRequest request) {
        return ResponseEntity.ok(batchService.updateBatch(id, request));
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "Actualizar cantidad de stock", description = "Realiza un ajuste directo en la cantidad de existencias de un lote específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock actualizado correctamente", content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cantidad de stock inválida"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<BatchResponse> updateStock(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(batchService.updateStock(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar lote", description = "Elimina un lote del sistema si no posee movimientos o dispensaciones asociadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lote eliminado correctamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<Void> deleteBatch(
            @Parameter(description = "ID del lote a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        batchService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/barcodes")
    @Operation(summary = "Listar códigos de barra del lote", description = "Devuelve los códigos de barras individuales generados o asignados a las unidades de este lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de códigos de barra del lote"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<List<StockBarcodeResponse>> getBarcodesByBatch(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(batchService.getBarcodesByBatchId(id));
    }

    @PostMapping("/{id}/barcodes")
    @Operation(summary = "Agregar códigos de barra al lote", description = "Asocia una serie de códigos de barra únicos adicionales a las unidades de este lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Códigos de barra agregados exitosamente"),
            @ApiResponse(responseCode = "400", description = "Formato de códigos inválido"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Lote no encontrado")
    })
    public ResponseEntity<List<StockBarcodeResponse>> addBarcodesToBatch(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody AddBarcodesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.addBarcodesToBatch(id, request));
    }

    @DeleteMapping("/{id}/barcodes")
    @Operation(summary = "Eliminar códigos de barra del lote", description = "Remueve códigos de barra individuales del lote especificado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Códigos de barra eliminados correctamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Lote o códigos no encontrados")
    })
    public ResponseEntity<Void> deleteBarcodesFromBatch(
            @Parameter(description = "ID del lote", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Lista de IDs de códigos de barra a eliminar", example = "[1, 2, 3]", required = true)
            @RequestParam("barcodeIds") List<Long> barcodeIds) {
        batchService.deleteBarcodesFromBatch(id, barcodeIds);
        return ResponseEntity.noContent().build();
    }
}

