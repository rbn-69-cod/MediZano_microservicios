package com.medizano.inventario.catalogo.service;

import com.medizano.inventario.catalogo.dto.CreateMedicineRequest;
import com.medizano.inventario.catalogo.dto.MedicineResponse;
import com.medizano.inventario.catalogo.dto.UpdateMedicineRequest;
import com.medizano.inventario.catalogo.entity.Medicine;
import com.medizano.inventario.catalogo.repository.MedicineRepository;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.InventarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MedicineService {

    private static final Logger log = LoggerFactory.getLogger(MedicineService.class);

    private final MedicineRepository medicineRepository;
    private final BatchRepository batchRepository;
    private final InventarioRepository inventarioRepository;

    public MedicineService(MedicineRepository medicineRepository, BatchRepository batchRepository,
                           InventarioRepository inventarioRepository) {
        this.medicineRepository = medicineRepository;
        this.batchRepository = batchRepository;
        this.inventarioRepository = inventarioRepository;
    }

    @Transactional
    public MedicineResponse createMedicine(CreateMedicineRequest request) {
        if (medicineRepository.existsByHsnCode(request.getHsnCode())) {
            throw new IllegalArgumentException("Ya existe un medicamento con el código HSN: " + request.getHsnCode());
        }

        Medicine medicine = new Medicine();
        medicine.setName(request.getName().trim());
        medicine.setManufacturer(request.getManufacturer().trim());
        medicine.setCategory(request.getCategory() != null ? request.getCategory().trim() : "General");
        medicine.setBarcode(request.getBarcode() != null ? request.getBarcode().trim() : null);
        medicine.setHsnCode(request.getHsnCode().trim());
        medicine.setGstPercentage(request.getGstPercentage());
        medicine.setPrescriptionRequired(request.getPrescriptionRequired());
        medicine.setStatus(Medicine.Status.ACTIVE);
        medicine.setPurchasePrice(request.getPurchasePrice());
        medicine.setSellingPrice(request.getSellingPrice());

        medicine = medicineRepository.save(medicine);
        createInitialBatchWhenProvided(medicine, request);
        log.info("Medicamento creado con ID: {}", medicine.getId());
        return mapToResponse(medicine, null);
    }

    @Transactional(readOnly = true)
    public MedicineResponse getMedicineById(Long id) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con ID: " + id));
        return mapToResponse(m, null);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> getAllMedicines() {
        return medicineRepository.findAll().stream()
                .map(m -> mapToResponse(m, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> searchMedicines(String name) {
        return medicineRepository.findByNameContainingIgnoreCase(name).stream()
                .map(m -> mapToResponse(m, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MedicineResponse findMedicineByBarcode(String barcode) {
        Medicine m = medicineRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con código de barras: " + barcode));
        return mapToResponse(m, null);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> searchMedicinesByBarcodePrefix(String prefix) {
        return medicineRepository.findByBarcodeStartingWith(prefix).stream()
                .map(m -> mapToResponse(m, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public MedicineResponse updateMedicineStatus(Long id, String statusStr) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con ID: " + id));
        Medicine.Status status = Medicine.Status.valueOf(statusStr.toUpperCase());
        m.setStatus(status);
        m = medicineRepository.save(m);
        return mapToResponse(m, null);
    }

    @Transactional
    public MedicineResponse updateMedicine(Long id, UpdateMedicineRequest request) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con ID: " + id));
        m.setName(request.getName());
        m.setManufacturer(request.getManufacturer());
        m.setCategory(request.getCategory());
        m.setBarcode(request.getBarcode());
        m.setHsnCode(request.getHsnCode());
        m.setGstPercentage(request.getGstPercentage());
        m.setPrescriptionRequired(request.getPrescriptionRequired());
        if (request.getPurchasePrice() != null) {
            m.setPurchasePrice(request.getPurchasePrice());
        }
        if (request.getSellingPrice() != null) {
            m.setSellingPrice(request.getSellingPrice());
        }
        m = medicineRepository.save(m);
        return mapToResponse(m, null);
    }

    @Transactional
    public void deleteMedicine(Long id) {
        if (!medicineRepository.existsById(id)) {
            throw new RuntimeException("Medicamento no encontrado con ID: " + id);
        }
        medicineRepository.deleteById(id);
        log.info("Medicamento eliminado con ID: {}", id);
    }

    private MedicineResponse mapToResponse(Medicine m, Integer stock) {
        MedicineResponse r = new MedicineResponse();
        r.setId(m.getId());
        r.setName(m.getName());
        r.setManufacturer(m.getManufacturer());
        r.setCategory(m.getCategory());
        r.setBarcode(m.getBarcode());
        r.setHsnCode(m.getHsnCode());
        r.setGstPercentage(m.getGstPercentage());
        r.setPrescriptionRequired(m.getPrescriptionRequired());
        r.setStatus(m.getStatus());
        // El catálogo y todos los módulos deben reflejar exactamente la suma
        // de lotes físicos, no un valor temporal enviado por el formulario.
        int s = stock != null ? stock : stockDisponible(m.getId());
        r.setTotalStock(s);
        r.setAvailableStock(s);
        r.setLowStock(s > 0 && s < 10);
        r.setOutOfStock(s <= 0);
        r.setLowStockThreshold(10);
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());

        r.setSellingPrice(m.getSellingPrice());
        r.setPurchasePrice(m.getPurchasePrice());

        return r;
    }

    private void createInitialBatchWhenProvided(Medicine medicine, CreateMedicineRequest request) {
        Integer initialStock = request.getInitialStock();
        if (initialStock == null || initialStock <= 0) {
            return;
        }

        if (request.getPurchasePrice() == null || request.getSellingPrice() == null) {
            throw new IllegalArgumentException("Para registrar stock inicial indica costo y precio de venta.");
        }

        String batchNumber = request.getBatchNumber() == null || request.getBatchNumber().isBlank()
                ? "LOT-" + medicine.getId() + "-" + System.currentTimeMillis()
                : request.getBatchNumber().trim();
        if (batchRepository.findByBatchNumber(batchNumber).isPresent()) {
            throw new IllegalArgumentException("Ya existe un lote con el número: " + batchNumber);
        }

        Batch batch = Batch.builder()
                .medicineId(medicine.getId())
                .medicineName(medicine.getName())
                .batchNumber(batchNumber)
                .expiryDate(request.getExpiryDate() != null ? request.getExpiryDate() : LocalDate.now().plusYears(2))
                .purchasePrice(request.getPurchasePrice())
                .sellingPrice(request.getSellingPrice())
                .quantityAvailable(initialStock)
                .estado(EstadoLote.DISPONIBLE)
                .build();
        batchRepository.save(batch);
        syncInventarioStock(medicine.getId());
    }

    private int stockDisponible(Long medicineId) {
        return batchRepository.findByMedicineId(medicineId).stream()
                .map(Batch::getQuantityAvailable)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void syncInventarioStock(Long medicineId) {
        int total = stockDisponible(medicineId);
        inventarioRepository.findByProductoId(medicineId).ifPresentOrElse(
                inventario -> {
                    inventario.setStockActual(total);
                    inventarioRepository.save(inventario);
                },
                () -> inventarioRepository.save(Inventario.builder()
                        .productoId(medicineId)
                        .stockActual(total)
                        .stockMinimo(5)
                        .build())
        );
    }
}

