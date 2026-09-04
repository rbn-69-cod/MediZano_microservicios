package com.medizano.catalogo.config;

import com.medizano.catalogo.entity.Medicine;
import com.medizano.catalogo.entity.Producto;
import com.medizano.catalogo.repository.MedicineRepository;
import com.medizano.catalogo.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final MedicineRepository medicineRepository;
    private final ProductoRepository productoRepository;

    @Bean
    public CommandLineRunner initCatalogoData() {
        return args -> {
            seedMedicine("Paracetamol 500 mg", "Portugal", "Analgésico / Antipirético",
                    "7751234567890", "MED-PAR-500-001", new BigDecimal("18.00"), false,
                    new BigDecimal("2.00"), new BigDecimal("5.00"));

            seedMedicine("Amoxicilina 500 mg", "Laboratorios Bagó", "Antibiótico",
                    "7759876543210", "MED-AMX-500-002", new BigDecimal("18.00"), true,
                    new BigDecimal("8.00"), new BigDecimal("15.50"));

            seedMedicine("Ibuprofeno 400 mg", "Genfar", "Antiinflamatorio",
                    "7755554443332", "MED-IBU-400-003", new BigDecimal("18.00"), false,
                    new BigDecimal("3.50"), new BigDecimal("8.00"));

            log.info(">>> [catalogo-ms] Datos iniciales de catálogo inicializados y sincronizados.");
        };
    }

    private void seedMedicine(String name, String manufacturer, String category,
                              String barcode, String hsnCode, BigDecimal gst, boolean prescription,
                              BigDecimal purchasePrice, BigDecimal sellingPrice) {
        Medicine m = medicineRepository.findByHsnCode(hsnCode).orElse(null);
        if (m == null) {
            m = Medicine.builder()
                    .name(name)
                    .manufacturer(manufacturer)
                    .category(category)
                    .barcode(barcode)
                    .hsnCode(hsnCode)
                    .gstPercentage(gst)
                    .prescriptionRequired(prescription)
                    .status(Medicine.Status.ACTIVE)
                    .purchasePrice(purchasePrice)
                    .sellingPrice(sellingPrice)
                    .build();
            m = medicineRepository.save(m);
            log.info(">>> [catalogo-ms] Creado medicamento: {} (S/ {})", name, sellingPrice);
        } else {
            m.setName(name);
            m.setManufacturer(manufacturer);
            m.setCategory(category);
            m.setBarcode(barcode);
            m.setPurchasePrice(purchasePrice);
            m.setSellingPrice(sellingPrice);
            m = medicineRepository.save(m);
        }

        // Sincronizar en tabla productos
        Producto p = productoRepository.findByCodigo(barcode).orElse(null);
        if (p == null) {
            p = Producto.builder()
                    .nombre(name)
                    .codigo(barcode)
                    .descripcion(category + " - " + manufacturer)
                    .precioCompra(purchasePrice)
                    .precioVenta(sellingPrice)
                    .estado(true)
                    .build();
        } else {
            p.setNombre(name);
            p.setPrecioCompra(purchasePrice);
            p.setPrecioVenta(sellingPrice);
            p.setEstado(true);
        }
        productoRepository.save(p);
    }
}

