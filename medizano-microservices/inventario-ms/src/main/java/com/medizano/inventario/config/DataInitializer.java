package com.medizano.inventario.config;

import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.repository.BatchRepository;
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

    @Bean
    public CommandLineRunner initInventarioData() {
        return args -> {
            seedBatch(1L, "Paracetamol 500 mg", "LOT-PAR-2026-001",
                    LocalDate.now().plusYears(2), new BigDecimal("2.00"), new BigDecimal("5.00"), 150);
            seedBatch(7L, "Paracetamol 500 mg", "LOT-PAR-2026-007",
                    LocalDate.now().plusYears(2), new BigDecimal("2.00"), new BigDecimal("5.00"), 150);

            seedBatch(2L, "Amoxicilina 500 mg", "LOT-AMX-2026-002",
                    LocalDate.now().plusYears(2), new BigDecimal("8.00"), new BigDecimal("15.50"), 80);
            seedBatch(8L, "Amoxicilina 500 mg", "LOT-AMX-2026-008",
                    LocalDate.now().plusYears(2), new BigDecimal("8.00"), new BigDecimal("15.50"), 80);

            seedBatch(3L, "Ibuprofeno 400 mg", "LOT-IBU-2026-003",
                    LocalDate.now().plusYears(2), new BigDecimal("3.50"), new BigDecimal("8.00"), 120);
            seedBatch(9L, "Ibuprofeno 400 mg", "LOT-IBU-2026-009",
                    LocalDate.now().plusYears(2), new BigDecimal("3.50"), new BigDecimal("8.00"), 120);

            log.info(">>> [inventario-ms] Lotes farmacéuticos iniciales sincronizados.");
        };
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
            b.setSellingPrice(sellingPrice);
            b.setPurchasePrice(purchasePrice);
            b.setQuantityAvailable(qty);
            b.setExpiryDate(expiryDate);
            batchRepository.save(b);
        }
    }
}
