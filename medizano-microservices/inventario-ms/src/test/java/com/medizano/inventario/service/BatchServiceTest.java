package com.medizano.inventario.service;

import com.medizano.inventario.dto.BatchResponse;
import com.medizano.inventario.dto.UpdateStockRequest;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.StockBarcodeRepository;
import com.medizano.inventario.repository.InventarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockBarcodeRepository stockBarcodeRepository;

    @Mock
    private InventarioRepository inventarioRepository;

    @InjectMocks
    private BatchService batchService;

    private Batch sampleBatch;

    @BeforeEach
    void setUp() {
        sampleBatch = Batch.builder()
                .id(1L)
                .medicineId(1L)
                .medicineName("Paracetamol 500 mg")
                .batchNumber("LOT-PAR-2026-001")
                .expiryDate(LocalDate.now().plusMonths(18))
                .purchasePrice(new BigDecimal("2.00"))
                .sellingPrice(new BigDecimal("5.00"))
                .quantityAvailable(150)
                .build();
    }

    @Test
    @DisplayName("1. Obtener lotes por medicamento")
    void testGetBatchesByMedicine() {
        when(batchRepository.findByMedicineId(1L)).thenReturn(List.of(sampleBatch));

        List<BatchResponse> batches = batchService.getBatchesByMedicine(1L);

        assertNotNull(batches);
        assertEquals(1, batches.size());
        assertEquals("LOT-PAR-2026-001", batches.get(0).getBatchNumber());
        assertEquals(150, batches.get(0).getQuantityAvailable());
        assertEquals(new BigDecimal("5.00"), batches.get(0).getSellingPrice());
    }

    @Test
    @DisplayName("2. Listar lotes vencidos")
    void testGetExpiredBatches() {
        Batch expiredBatch = Batch.builder()
                .id(2L)
                .medicineId(1L)
                .batchNumber("LOT-EXPIRED")
                .expiryDate(LocalDate.now().minusDays(10))
                .quantityAvailable(20)
                .build();

        when(batchRepository.findExpiredBatches(any(LocalDate.class)))
                .thenReturn(List.of(expiredBatch));

        List<BatchResponse> expired = batchService.getExpiredBatches();

        assertNotNull(expired);
        assertEquals(1, expired.size());
        assertEquals("LOT-EXPIRED", expired.get(0).getBatchNumber());
    }

    @Test
    @DisplayName("3. Listar lotes con bajo stock")
    void testGetLowStockBatches() {
        sampleBatch.setQuantityAvailable(5);
        when(batchRepository.findLowStockBatches(eq(10), any(LocalDate.class)))
                .thenReturn(List.of(sampleBatch));

        List<BatchResponse> lowStock = batchService.getLowStockBatches(10);

        assertNotNull(lowStock);
        assertEquals(1, lowStock.size());
        assertEquals(5, lowStock.get(0).getQuantityAvailable());
    }

    @Test
    @DisplayName("4. Actualizar stock de lote")
    void testUpdateStock() {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(sampleBatch));
        when(batchRepository.save(any(Batch.class))).thenReturn(sampleBatch);

        UpdateStockRequest req = new UpdateStockRequest();
        req.setQuantityAvailable(200);

        BatchResponse updated = batchService.updateStock(1L, req);

        assertNotNull(updated);
        assertEquals(200, updated.getQuantityAvailable());
        verify(batchRepository).save(sampleBatch);
    }
}
