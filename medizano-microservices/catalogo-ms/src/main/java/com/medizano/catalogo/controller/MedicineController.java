package com.medizano.catalogo.controller;

import com.medizano.catalogo.dto.CreateMedicineRequest;
import com.medizano.catalogo.dto.MedicineResponse;
import com.medizano.catalogo.dto.UpdateMedicineRequest;
import com.medizano.catalogo.service.MedicineService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Medicamentos", description = "Gestión de Catálogo Farmacéutico")
public class MedicineController {

    private final MedicineService medicineService;

    @PostMapping
    @Operation(summary = "Crear medicamento")
    public ResponseEntity<MedicineResponse> createMedicine(@Valid @RequestBody CreateMedicineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicineService.createMedicine(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener medicamento por ID")
    public ResponseEntity<MedicineResponse> getMedicineById(@PathVariable Long id) {
        return ResponseEntity.ok(medicineService.getMedicineById(id));
    }

    @GetMapping
    @Operation(summary = "Listar todos los medicamentos")
    public ResponseEntity<List<MedicineResponse>> getAllMedicines() {
        return ResponseEntity.ok(medicineService.getAllMedicines());
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar medicamentos por nombre")
    public ResponseEntity<List<MedicineResponse>> searchMedicines(@RequestParam String name) {
        return ResponseEntity.ok(medicineService.searchMedicines(name));
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Buscar medicamento por código de barras")
    public ResponseEntity<MedicineResponse> findMedicineByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(medicineService.findMedicineByBarcode(barcode));
    }

    @GetMapping("/barcode/search")
    @Operation(summary = "Buscar medicamentos por prefijo de código de barras")
    public ResponseEntity<List<MedicineResponse>> searchMedicinesByBarcode(@RequestParam String prefix) {
        return ResponseEntity.ok(medicineService.searchMedicinesByBarcodePrefix(prefix));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado de medicamento")
    public ResponseEntity<MedicineResponse> updateMedicineStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(medicineService.updateMedicineStatus(id, status));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar detalles de medicamento")
    public ResponseEntity<MedicineResponse> updateMedicine(@PathVariable Long id, @Valid @RequestBody UpdateMedicineRequest request) {
        return ResponseEntity.ok(medicineService.updateMedicine(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar medicamento")
    public ResponseEntity<Void> deleteMedicine(@PathVariable Long id) {
        medicineService.deleteMedicine(id);
        return ResponseEntity.noContent().build();
    }
}

