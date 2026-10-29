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
    private final com.medizano.inventario.repository.MovimientoInventarioRepository movimientoRepository;

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
                .estado(request.getEstado() != null ? request.getEstado() : com.medizano.inventario.entity.EstadoLote.DISPONIBLE)
                .proveedorId(request.getProveedorId())
                .numeroFacturaCompra(request.getNumeroFacturaCompra())
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
        // Cantidades protegidas: no se sobreescribe stock desde edicion basica
        if (request.getEstado() != null) {
            batch.setEstado(request.getEstado());
        }
        if (request.getProveedorId() != null) {
            batch.setProveedorId(request.getProveedorId());
        }
        if (request.getNumeroFacturaCompra() != null) {
            batch.setNumeroFacturaCompra(request.getNumeroFacturaCompra());
        }
        if (request.getEstado() != null) {
            batch.setEstado(request.getEstado());
        }
        if (request.getProveedorId() != null) {
            batch.setProveedorId(request.getProveedorId());
        }
        if (request.getNumeroFacturaCompra() != null) {
            batch.setNumeroFacturaCompra(request.getNumeroFacturaCompra());
        }

        batch = batchRepository.save(batch);
        syncInventarioStock(batch.getMedicineId());
        return mapToResponse(batch);
    }

    @Transactional
    public BatchResponse updateStock(Long id, UpdateStockRequest request) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + id));

        int oldQty = batch.getQuantityAvailable() != null ? batch.getQuantityAvailable() : 0;
        int newQty = request.getQuantityAvailable();
        int diff = newQty - oldQty;

        if (diff != 0) {
            batch.setQuantityAvailable(newQty);
            batch = batchRepository.save(batch);
            syncInventarioStock(batch.getMedicineId());

            String motivo = (request.getMotivo() != null && !request.getMotivo().isBlank())
                    ? request.getMotivo().trim()
                    : "Ajuste de inventario fisico verificado";

            com.medizano.inventario.entity.MovimientoInventario.TipoMovimiento tipoMov = diff > 0
                    ? com.medizano.inventario.entity.MovimientoInventario.TipoMovimiento.ENTRADA
                    : com.medizano.inventario.entity.MovimientoInventario.TipoMovimiento.SALIDA;

            com.medizano.inventario.entity.MovimientoInventario mov = com.medizano.inventario.entity.MovimientoInventario.builder()
                    .productoId(batch.getMedicineId())
                    .tipo(tipoMov)
                    .cantidad(Math.abs(diff))
                    .referencia("Ajuste manual [Lote: " + batch.getBatchNumber() + "] (" + oldQty + " -> " + newQty + ") - " + motivo)
                    .fecha(java.time.LocalDateTime.now())
                    .build();
            movimientoRepository.save(mov);
            log.info("Ajuste de stock registrado: lote={}, diff={}, motivo={}", batch.getBatchNumber(), diff, motivo);
        }

        return mapToResponse(batch);
    }

    @Transactional
    public void deleteBatch(Long id) {
        if (id == null) {
            log.warn("Solicitud de eliminación de lote ignorada: ID es nulo");
            return;
        }
        Batch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            log.warn("Lote ID {} no encontrado para eliminación", id);
            return;
        }
        Long medId = batch.getMedicineId();

        boolean hasHistory = (batch.getNumeroFacturaCompra() != null && !batch.getNumeroFacturaCompra().isBlank())
                || (batch.getQuantityAvailable() != null && batch.getQuantityAvailable() > 0)
                || (medId != null && movimientoRepository.findByProductoIdOrderByFechaDesc(medId).size() > 0);

        if (hasHistory) {
            log.info("Lote {} posee historial de compra/movimientos. Aplicando retiro logico seguro.", batch.getBatchNumber());
            batch.setEstado(com.medizano.inventario.entity.EstadoLote.RETIRAR_MOSTRADOR);
            batch.setQuantityAvailable(0);
            batchRepository.save(batch);

            if (medId != null) {
                com.medizano.inventario.entity.MovimientoInventario mov = com.medizano.inventario.entity.MovimientoInventario.builder()
                        .productoId(medId)
                        .tipo(com.medizano.inventario.entity.MovimientoInventario.TipoMovimiento.SALIDA)
                        .cantidad(0)
                        .referencia("Retiro logico seguro [Lote: " + batch.getBatchNumber() + "] (conservando historial de compra/kardex)")
                        .fecha(java.time.LocalDateTime.now())
                        .build();
                movimientoRepository.save(mov);
            }
        } else {
            try {
                stockBarcodeRepository.deleteAll(stockBarcodeRepository.findByBatchId(batch.getId()));
            } catch (Exception ex) {
                log.warn("No se pudieron eliminar codigos de barra previos: {}", ex.getMessage());
            }
            batchRepository.delete(batch);
        }
        if (medId != null) {
            syncInventarioStock(medId);
        }
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
                .estado(b.getEstado())
                .proveedorId(b.getProveedorId())
                .numeroFacturaCompra(b.getNumeroFacturaCompra())
                .fechaUltimaEvaluacion(b.getFechaUltimaEvaluacion())
                .expired(b.isExpired())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}

