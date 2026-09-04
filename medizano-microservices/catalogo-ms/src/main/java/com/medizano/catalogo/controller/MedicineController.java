package com.medizano.catalogo.controller;

import com.medizano.catalogo.dto.CreateMedicineRequest;
import com.medizano.catalogo.dto.MedicineResponse;
import com.medizano.catalogo.dto.UpdateMedicineRequest;
import com.medizano.catalogo.service.MedicineService;
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
@RequestMapping("/api/pharmacist/medicines")
@RequiredArgsConstructor
@Tag(name = "Medicamentos", description = "Gestión integral del catálogo farmacéutico, búsqueda por código de barras y control de stock")
public class MedicineController {

    private final MedicineService medicineService;

    @PostMapping
    @Operation(summary = "Crear medicamento", description = "Registra un nuevo medicamento en el catálogo farmacéutico con especificaciones técnicas, precio, porcentaje de IGV y opcionalmente su lote inicial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Medicamento creado exitosamente", content = @Content(schema = @Schema(implementation = MedicineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o faltantes"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol PHARMACIST o ADMIN"),
            @ApiResponse(responseCode = "409", description = "Conflicto - Ya existe un medicamento con el mismo nombre o código de barras")
    })
    public ResponseEntity<MedicineResponse> createMedicine(@Valid @RequestBody CreateMedicineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicineService.createMedicine(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener medicamento por ID", description = "Retorna el detalle completo de un medicamento registrado según su identificador primario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento encontrado", content = @Content(schema = @Schema(implementation = MedicineResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT ausente o expirado"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado con el ID proporcionado")
    })
    public ResponseEntity<MedicineResponse> getMedicineById(
            @Parameter(description = "ID único del medicamento en base de datos", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(medicineService.getMedicineById(id));
    }

    @GetMapping
    @Operation(summary = "Listar todos los medicamentos", description = "Devuelve la lista completa de medicamentos registrados en el catálogo con sus estados de inventario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de medicamentos recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT ausente o inválido")
    })
    public ResponseEntity<List<MedicineResponse>> getAllMedicines() {
        return ResponseEntity.ok(medicineService.getAllMedicines());
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar medicamentos por nombre", description = "Busca medicamentos cuyo nombre comercial o genérico coincida parcialmente con el término de búsqueda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos que coinciden con el criterio"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT requerido")
    })
    public ResponseEntity<List<MedicineResponse>> searchMedicines(
            @Parameter(description = "Nombre comercial o genérico a buscar", example = "Paracetamol", required = true)
            @RequestParam String name) {
        return ResponseEntity.ok(medicineService.searchMedicines(name));
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Buscar medicamento por código de barras", description = "Obtiene la ficha técnica del medicamento escaneado mediante su código de barras (EAN-13 / UPC).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento localizado por código de barras", content = @Content(schema = @Schema(implementation = MedicineResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT requerido"),
            @ApiResponse(responseCode = "404", description = "No existe ningún medicamento asociado al código de barras ingresado")
    })
    public ResponseEntity<MedicineResponse> findMedicineByBarcode(
            @Parameter(description = "Código de barras único del medicamento", example = "7751234567890", required = true)
            @PathVariable String barcode) {
        return ResponseEntity.ok(medicineService.findMedicineByBarcode(barcode));
    }

    @GetMapping("/barcode/search")
    @Operation(summary = "Buscar medicamentos por prefijo de código de barras", description = "Permite autocompletado y búsqueda predictiva mediante los primeros dígitos de un código de barras.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de medicamentos con códigos que inician con el prefijo"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT requerido")
    })
    public ResponseEntity<List<MedicineResponse>> searchMedicinesByBarcode(
            @Parameter(description = "Prefijo numérico del código de barras", example = "775", required = true)
            @RequestParam String prefix) {
        return ResponseEntity.ok(medicineService.searchMedicinesByBarcodePrefix(prefix));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado de medicamento", description = "Modifica el estado operativo del medicamento (ACTIVE, INACTIVE, DISCONTINUED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del medicamento actualizado con éxito", content = @Content(schema = @Schema(implementation = MedicineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estado solicitado inválido"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol PHARMACIST o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<MedicineResponse> updateMedicineStatus(
            @Parameter(description = "ID del medicamento", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Nuevo estado: ACTIVE, INACTIVE o DISCONTINUED", example = "ACTIVE", required = true)
            @RequestParam String status) {
        return ResponseEntity.ok(medicineService.updateMedicineStatus(id, status));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar detalles de medicamento", description = "Actualiza las características, fabricante, categoría, código HSN o precios de un medicamento existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento actualizado exitosamente", content = @Content(schema = @Schema(implementation = MedicineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Cuerpo de solicitud inválido o valores fuera de rango"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol PHARMACIST o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado con el ID provisto")
    })
    public ResponseEntity<MedicineResponse> updateMedicine(
            @Parameter(description = "ID del medicamento a actualizar", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateMedicineRequest request) {
        return ResponseEntity.ok(medicineService.updateMedicine(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar medicamento", description = "Desactiva o elimina lógicamente un medicamento del catálogo si no presenta transacciones activas asociadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Medicamento eliminado correctamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado con el ID provisto")
    })
    public ResponseEntity<Void> deleteMedicine(
            @Parameter(description = "ID del medicamento a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        medicineService.deleteMedicine(id);
        return ResponseEntity.noContent().build();
    }
}

