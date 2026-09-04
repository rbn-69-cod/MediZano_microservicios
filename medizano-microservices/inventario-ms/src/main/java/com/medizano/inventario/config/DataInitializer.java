package com.medizano.inventario.config;

import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.InventarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final BatchRepository batchRepository;
    private final InventarioRepository inventarioRepository;

    @Bean
    public CommandLineRunner initInventarioData() {
        return args -> {
            seedBatch(1L, "Paracetamol 500 mg", "LOT-PAR-2026-001",
                    LocalDate.now().plusYears(2), new BigDecimal("2.00"), new BigDecimal("5.00"), 150);
            seedInventario(1L, 150, 10);

            seedBatch(2L, "Amoxicilina 500 mg", "LOT-AMX-2026-002",
                    LocalDate.now().plusYears(2), new BigDecimal("8.00"), new BigDecimal("15.50"), 100);
            seedInventario(2L, 100, 10);

            seedBatch(3L, "Ibuprofeno 400 mg", "LOT-IBU-2026-003",
                    LocalDate.now().plusYears(2), new BigDecimal("3.50"), new BigDecimal("8.00"), 120);
            seedInventario(3L, 120, 10);

            log.info(">>> [inventario-ms] Lotes e inventario general sincronizados e idempotentes.");
        };
    }

    private void seedInventario(Long productoId, int stockActual, int stockMinimo) {
        Inventario inv = inventarioRepository.findByProductoId(productoId).orElse(null);
        if (inv == null) {
            inv = Inventario.builder()
                    .productoId(productoId)
                    .stockActual(stockActual)
                    .stockMinimo(stockMinimo)
                    .build();
            inventarioRepository.save(inv);
        } else {
            // No sobreescribir stockActual si ya existe para persistir ventas tras reinicio
            inv.setStockMinimo(stockMinimo);
            inventarioRepository.save(inv);
        }
    }

    private void seedBatch(Long medicineId, String medicineName, String batchNumber,
                           LocalDate expiryDate, BigDecimal purchasePrice, BigDecimal sellingPrice, int qty) {
        Batch b = batchRepository.findByBatchNumber(batchNumber).orElse(null);
        if (b == null) {
            b = Batch.builder()
                    .medicineId(medicineId)
                    .medicineName(medicineName)
                    .batchNumber(batchNumber)
                    .expiryDate(expiryDate)
                    .purchasePrice(purchasePrice)
                    .sellingPrice(sellingPrice)
                    .quantityAvailable(qty)
                    .build();
            batchRepository.save(b);
            log.info(">>> [inventario-ms] Creado lote: {} para medicamento {}", batchNumber, medicineName);
        } else {
            // Conservar quantityAvailable existente para no sobreescribir ventas tras reinicio
            b.setSellingPrice(sellingPrice);
            b.setPurchasePrice(purchasePrice);
            b.setExpiryDate(expiryDate);
            batchRepository.save(b);
        }
    }
}
