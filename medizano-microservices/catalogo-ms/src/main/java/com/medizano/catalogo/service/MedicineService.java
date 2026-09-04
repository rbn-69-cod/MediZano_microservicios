package com.medizano.catalogo.service;

import com.medizano.catalogo.dto.CreateMedicineRequest;
import com.medizano.catalogo.dto.MedicineResponse;
import com.medizano.catalogo.dto.UpdateMedicineRequest;
import com.medizano.catalogo.entity.Medicine;
import com.medizano.catalogo.entity.Producto;
import com.medizano.catalogo.repository.MedicineRepository;
import com.medizano.catalogo.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MedicineService {

    private static final Logger log = LoggerFactory.getLogger(MedicineService.class);

    private final MedicineRepository medicineRepository;
    private final ProductoRepository productoRepository;

    public MedicineService(MedicineRepository medicineRepository, ProductoRepository productoRepository) {
        this.medicineRepository = medicineRepository;
        this.productoRepository = productoRepository;
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

        // Sincronizar con tabla productos
        try {
            Producto p = new Producto();
            p.setNombre(medicine.getName());
            p.setCodigo(medicine.getBarcode() != null ? medicine.getBarcode() : "MED-" + medicine.getId());
            p.setDescripcion(medicine.getCategory() + " - " + medicine.getManufacturer());
            p.setPrecioVenta(request.getSellingPrice() != null ? request.getSellingPrice() : BigDecimal.TEN);
            p.setPrecioCompra(request.getPurchasePrice() != null ? request.getPurchasePrice() : BigDecimal.valueOf(5));
            p.setEstado(true);
            productoRepository.save(p);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar medicamento con productos: {}", e.getMessage());
        }

        log.info("Medicamento creado con ID: {}", medicine.getId());
        return mapToResponse(medicine, request.getInitialStock() != null ? request.getInitialStock() : 100);
    }

    @Transactional(readOnly = true)
    public MedicineResponse getMedicineById(Long id) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con ID: " + id));
        return mapToResponse(m, 100);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> getAllMedicines() {
        return medicineRepository.findAll().stream()
                .map(m -> mapToResponse(m, 100))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> searchMedicines(String name) {
        return medicineRepository.findByNameContainingIgnoreCase(name).stream()
                .map(m -> mapToResponse(m, 100))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MedicineResponse findMedicineByBarcode(String barcode) {
        Medicine m = medicineRepository.findByBarcode(barcode)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con código de barras: " + barcode));
        return mapToResponse(m, 100);
    }

    @Transactional(readOnly = true)
    public List<MedicineResponse> searchMedicinesByBarcodePrefix(String prefix) {
        return medicineRepository.findByBarcodeStartingWith(prefix).stream()
                .map(m -> mapToResponse(m, 100))
                .collect(Collectors.toList());
    }

    @Transactional
    public MedicineResponse updateMedicineStatus(Long id, String statusStr) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado con ID: " + id));
        Medicine.Status status = Medicine.Status.valueOf(statusStr.toUpperCase());
        m.setStatus(status);
        m = medicineRepository.save(m);
        return mapToResponse(m, 100);
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
        return mapToResponse(m, 100);
    }

    @Transactional
    public void deleteMedicine(Long id) {
        medicineRepository.deleteById(id);
    }

    private MedicineResponse mapToResponse(Medicine m, int stock) {
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
        r.setTotalStock(stock);
        r.setAvailableStock(stock);
        r.setLowStock(stock < 10);
        r.setOutOfStock(stock <= 0);
        r.setLowStockThreshold(10);
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());

        BigDecimal selling = m.getSellingPrice();
        BigDecimal purchase = m.getPurchasePrice();
        if (selling == null && m.getBarcode() != null) {
            Producto prod = productoRepository.findByCodigo(m.getBarcode()).orElse(null);
            if (prod != null) {
                selling = prod.getPrecioVenta();
                purchase = prod.getPrecioCompra();
            }
        }
        r.setSellingPrice(selling);
        r.setPurchasePrice(purchase);

        return r;
    }
}

