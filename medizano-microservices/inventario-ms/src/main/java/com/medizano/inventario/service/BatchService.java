package com.medizano.inventario.service;

import com.medizano.inventario.dto.*;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.StockBarcode;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.StockBarcodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.repository.InventarioRepository;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final BatchRepository batchRepository;
    private final StockBarcodeRepository stockBarcodeRepository;
    private final InventarioRepository inventarioRepository;

    private void syncInventarioStock(Long medicineId) {
        if (medicineId == null) return;
        int total = batchRepository.findByMedicineId(medicineId).stream()
                .mapToInt(b -> b.getQuantityAvailable() != null ? b.getQuantityAvailable() : 0)
                .sum();
        inventarioRepository.findByProductoId(medicineId).ifPresentOrElse(
                inv -> {
                    inv.setStockActual(total);
                    inventarioRepository.save(inv);
                },
                () -> {
                    inventarioRepository.save(Inventario.builder()
                            .productoId(medicineId)
                            .stockActual(total)
                            .stockMinimo(5)
                            .build());
                }
        );
    }

    @Transactional
    public BatchResponse createBatch(CreateBatchRequest request) {
        Batch batch = Batch.builder()
                .medicineId(request.getMedicineId())
                .medicineName("Medicamento #" + request.getMedicineId())
                .batchNumber(request.getBatchNumber())
                .expiryDate(request.getExpiryDate())
                .purchasePrice(request.getPurchasePrice())
                .sellingPrice(request.getSellingPrice())
                .quantityAvailable(request.getQuantityAvailable())
                .build();

        batch = batchRepository.save(batch);
        syncInventarioStock(batch.getMedicineId());

        if (request.getBarcodes() != null && !request.getBarcodes().isEmpty()) {
            for (String code : request.getBarcodes()) {
                if (code != null && !code.trim().isEmpty()) {
                    StockBarcode sb = StockBarcode.builder()
                            .batch(batch)
                            .barcode(code.trim())
                            .sold(false)
                            .build();
                    stockBarcodeRepository.save(sb);
                }
            }
        }

        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getBatchesByMedicine(Long medicineId) {
        return batchRepository.findByMedicineId(medicineId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getExpiredBatches() {
        return batchRepository.findExpiredBatches(LocalDate.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getLowStockBatches(Integer threshold) {
        return batchRepository.findLowStockBatches(threshold != null ? threshold : 10, LocalDate.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches() {
        return batchRepository.findAllOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatchById(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + id));
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatchByBarcode(String barcode) {
        StockBarcode sb = stockBarcodeRepository.findByBarcode(barcode.trim())
                .orElseThrow(() -> new RuntimeException("Código de barras no encontrado: " + barcode));
        return mapToResponse(sb.getBatch());
    }

    @Transactional
    public BatchResponse updateBatch(Long id, UpdateBatchRequest request) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + id));

        batch.setBatchNumber(request.getBatchNumber());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setPurchasePrice(request.getPurchasePrice());
        batch.setSellingPrice(request.getSellingPrice());
        if (request.getQuantityAvailable() != null) {
            batch.setQuantityAvailable(request.getQuantityAvailable());
        }

        batch = batchRepository.save(batch);
        syncInventarioStock(batch.getMedicineId());
        return mapToResponse(batch);
    }

    @Transactional
    public BatchResponse updateStock(Long id, UpdateStockRequest request) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + id));

        batch.setQuantityAvailable(request.getQuantityAvailable());
        batch = batchRepository.save(batch);
        syncInventarioStock(batch.getMedicineId());
        return mapToResponse(batch);
    }

    @Transactional
    public void deleteBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + id));
        Long medId = batch.getMedicineId();
        batchRepository.delete(batch);
        syncInventarioStock(medId);
    }

    @Transactional(readOnly = true)
    public List<StockBarcodeResponse> getBarcodesByBatchId(Long batchId) {
        return stockBarcodeRepository.findByBatchId(batchId).stream()
                .map(sb -> StockBarcodeResponse.builder()
                        .id(sb.getId())
                        .batchId(sb.getBatch().getId())
                        .barcode(sb.getBarcode())
                        .sold(sb.getSold())
                        .createdAt(sb.getCreatedAt())
                        .updatedAt(sb.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<StockBarcodeResponse> addBarcodesToBatch(Long batchId, AddBarcodesRequest request) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + batchId));

        for (String code : request.getBarcodes()) {
            if (code != null && !code.trim().isEmpty() && !stockBarcodeRepository.existsByBarcode(code.trim())) {
                StockBarcode sb = StockBarcode.builder()
                        .batch(batch)
                        .barcode(code.trim())
                        .sold(false)
                        .build();
                stockBarcodeRepository.save(sb);
            }
        }

        return getBarcodesByBatchId(batchId);
    }

    @Transactional
    public void deleteBarcodesFromBatch(Long batchId, List<Long> barcodeIds) {
        for (Long id : barcodeIds) {
            stockBarcodeRepository.findById(id).ifPresent(stockBarcodeRepository::delete);
        }
    }

    private BatchResponse mapToResponse(Batch b) {
        return BatchResponse.builder()
                .id(b.getId())
                .medicineId(b.getMedicineId())
                .medicineName(b.getMedicineName())
                .batchNumber(b.getBatchNumber())
                .expiryDate(b.getExpiryDate())
                .purchasePrice(b.getPurchasePrice())
                .sellingPrice(b.getSellingPrice())
                .quantityAvailable(b.getQuantityAvailable())
                .expired(b.isExpired())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}

