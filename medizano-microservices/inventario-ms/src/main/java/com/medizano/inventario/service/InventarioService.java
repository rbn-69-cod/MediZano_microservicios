package com.medizano.inventario.service;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.dto.RestockStockRequest;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.entity.MovimientoInventario;
import com.medizano.inventario.entity.InventoryOperation;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.InventarioRepository;
import com.medizano.inventario.repository.MovimientoInventarioRepository;
import com.medizano.inventario.repository.InventoryOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final BatchRepository batchRepository;
    private final InventoryOperationRepository operationRepository;

    @Transactional(readOnly = true)
    public List<InventarioDTO> listarInventario() {
        return inventarioRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventarioDTO obtenerPorProductoId(Long productoId) {
        Inventario inventario = inventarioRepository.findByProductoId(productoId)
                .orElse(Inventario.builder()
                        .productoId(productoId)
                        .stockActual(0)
                        .stockMinimo(5)
                        .build());
        return mapToDTO(inventario);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public InventarioDTO registrarEntrada(MovimientoRequest request) {
        Inventario inventario = inventarioRepository.findByProductoIdForUpdate(request.getProductoId())
                .orElseGet(() -> Inventario.builder()
                        .productoId(request.getProductoId())
                        .stockActual(0)
                        .stockMinimo(5)
                        .build());

        inventario.setStockActual(inventario.getStockActual() + request.getCantidad());
        inventario = inventarioRepository.save(inventario);

        MovimientoInventario mov = MovimientoInventario.builder()
                .productoId(request.getProductoId())
                .tipo(MovimientoInventario.TipoMovimiento.ENTRADA)
                .cantidad(request.getCantidad())
                .referencia(request.getReferencia() != null ? request.getReferencia() : "Ingreso manual")
                .fecha(LocalDateTime.now())
                .build();
        movimientoRepository.save(mov);

        log.info("Entrada de stock: productoId={}, cant={}, nuevoStock={}", request.getProductoId(), request.getCantidad(), inventario.getStockActual());
        return mapToDTO(inventario);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public InventarioDTO registrarSalida(MovimientoRequest request) {
        Inventario inventario = inventarioRepository.findByProductoIdForUpdate(request.getProductoId())
                .orElseThrow(() -> new RuntimeException("No existe registro de inventario para el producto: " + request.getProductoId()));

        if (inventario.getStockActual() < request.getCantidad()) {
            throw new RuntimeException("Stock insuficiente para el producto ID " + request.getProductoId() +
                    ". Stock actual: " + inventario.getStockActual() + ", solicitado: " + request.getCantidad());
        }

        inventario.setStockActual(inventario.getStockActual() - request.getCantidad());
        inventario = inventarioRepository.save(inventario);

        MovimientoInventario mov = MovimientoInventario.builder()
                .productoId(request.getProductoId())
                .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                .cantidad(request.getCantidad())
                .referencia(request.getReferencia() != null ? request.getReferencia() : "Salida manual")
                .fecha(LocalDateTime.now())
                .build();
        movimientoRepository.save(mov);

        log.info("Salida de stock: productoId={}, cant={}, nuevoStock={}", request.getProductoId(), request.getCantidad(), inventario.getStockActual());
        return mapToDTO(inventario);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void descontarStockVenta(DescuentoStockRequest request) {
        log.info("Procesando descuento atómico de stock para venta {}", request.getNumeroVenta());

        if (request.getNumeroVenta() == null || request.getNumeroVenta().isBlank()) {
            throw new IllegalArgumentException("El número de venta es obligatorio para garantizar idempotencia");
        }
        String operationKey = "SALE:" + request.getNumeroVenta().trim();
        if (operationRepository.existsByOperationKey(operationKey)) {
            log.info("Descuento {} ya fue procesado; respuesta idempotente", operationKey);
            return;
        }

        validarDisponibilidad(request);

        for (DescuentoStockRequest.ItemDescuento item : request.getItems()) {
            if (item.getBatchId() != null) {
                Batch batch = batchRepository.findByMedicineIdForUpdate(item.getProductoId()).stream()
                        .filter(candidate -> candidate.getId().equals(item.getBatchId()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getBatchId()));
                if (!batch.getMedicineId().equals(item.getProductoId())) {
                    throw new RuntimeException("El lote " + batch.getBatchNumber() + " no corresponde al producto ID " + item.getProductoId());
                }
                if (batch.isExpired()) {
                    throw new IllegalStateException("El lote " + batch.getBatchNumber() + " está vencido y no puede venderse");
                }
                if (batch.getQuantityAvailable() < item.getCantidad()) {
                    throw new RuntimeException("Stock insuficiente en el lote " + batch.getBatchNumber() +
                            ". Stock disponible: " + batch.getQuantityAvailable() + ", solicitado: " + item.getCantidad());
                }
                batch.setQuantityAvailable(batch.getQuantityAvailable() - item.getCantidad());
                batchRepository.save(batch);

                // Sincronizar el inventario general con la suma de todos los lotes del producto
                int totalStock = batchRepository.findByMedicineIdForUpdate(item.getProductoId()).stream()
                        .mapToInt(b -> b.getQuantityAvailable() != null ? b.getQuantityAvailable() : 0)
                        .sum();

                Inventario inventario = inventarioRepository.findByProductoIdForUpdate(item.getProductoId())
                        .orElseGet(() -> Inventario.builder()
                                .productoId(item.getProductoId())
                                .stockActual(totalStock)
                                .stockMinimo(5)
                                .build());
                inventario.setStockActual(totalStock);
                inventarioRepository.save(inventario);

                MovimientoInventario mov = MovimientoInventario.builder()
                        .productoId(item.getProductoId())
                        .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                        .cantidad(item.getCantidad())
                        .referencia("Venta " + request.getNumeroVenta() + " (Lote " + batch.getBatchNumber() + ")")
                        .fecha(LocalDateTime.now())
                        .build();
                movimientoRepository.save(mov);
            } else {
                Inventario inventario = inventarioRepository.findByProductoIdForUpdate(item.getProductoId())
                        .orElseGet(() -> {
                            int stockTotal = batchRepository.findByMedicineIdForUpdate(item.getProductoId()).stream()
                                    .mapToInt(b -> b.getQuantityAvailable() != null ? b.getQuantityAvailable() : 0)
                                    .sum();
                            Inventario nuevo = Inventario.builder()
                                    .productoId(item.getProductoId())
                                    .stockActual(stockTotal)
                                    .stockMinimo(5)
                                    .build();
                            return inventarioRepository.save(nuevo);
                        });

                if (inventario.getStockActual() < item.getCantidad()) {
                    throw new RuntimeException("Stock insuficiente para el producto ID " + item.getProductoId() +
                            ". Stock disponible: " + inventario.getStockActual() + ", requerido: " + item.getCantidad());
                }

                inventario.setStockActual(inventario.getStockActual() - item.getCantidad());
                inventarioRepository.save(inventario);

                // Descontar también en lotes asociados (estrategia FEFO)
                List<Batch> batches = batchRepository
                        .findSellableByMedicineIdForUpdate(item.getProductoId(), LocalDate.now());
                int pendiente = item.getCantidad();
                for (Batch b : batches) {
                    if (pendiente <= 0) break;
                    if (b.getQuantityAvailable() != null && b.getQuantityAvailable() > 0) {
                        int ded = Math.min(b.getQuantityAvailable(), pendiente);
                        b.setQuantityAvailable(b.getQuantityAvailable() - ded);
                        batchRepository.save(b);
                        pendiente -= ded;
                    }
                }

                MovimientoInventario mov = MovimientoInventario.builder()
                        .productoId(item.getProductoId())
                        .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                        .cantidad(item.getCantidad())
                        .referencia("Venta " + request.getNumeroVenta())
                        .fecha(LocalDateTime.now())
                        .build();
                movimientoRepository.save(mov);
            }
        }

        operationRepository.save(InventoryOperation.builder()
                .operationKey(operationKey)
                .operationType("SALE")
                .build());

        log.info("Descuento de stock completado exitosamente para venta {}", request.getNumeroVenta());
    }

    private void validarDisponibilidad(DescuentoStockRequest request) {
        for (DescuentoStockRequest.ItemDescuento item : request.getItems()) {
            if (item.getProductoId() == null || item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new IllegalArgumentException("Producto y cantidad positiva son obligatorios");
            }
            if (item.getBatchId() != null) {
                Batch batch = batchRepository.findByMedicineIdForUpdate(item.getProductoId()).stream()
                        .filter(candidate -> candidate.getId().equals(item.getBatchId()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getBatchId()));
                if (!batch.getMedicineId().equals(item.getProductoId()) || batch.isExpired()
                        || batch.getQuantityAvailable() < item.getCantidad()) {
                    throw new IllegalStateException("El lote indicado no está disponible para completar la venta");
                }
            } else {
                int available = batchRepository
                        .findSellableByMedicineIdForUpdate(item.getProductoId(), LocalDate.now())
                        .stream().mapToInt(b -> b.getQuantityAvailable() == null ? 0 : b.getQuantityAvailable()).sum();
                if (available < item.getCantidad()) {
                    throw new IllegalStateException("Stock vigente insuficiente para el producto ID " + item.getProductoId());
                }
            }
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void reponerStockDevolucion(RestockStockRequest request) {
        String operationKey = "RETURN:" + request.getReturnNumber().trim();
        if (operationRepository.existsByOperationKey(operationKey)) {
            return;
        }

        for (RestockStockRequest.ItemRestock item : request.getItems()) {
            Batch batch = batchRepository.findByMedicineIdForUpdate(item.getProductoId()).stream()
                    .filter(candidate -> candidate.getId().equals(item.getBatchId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getBatchId()));
            if (!batch.getMedicineId().equals(item.getProductoId())) {
                throw new IllegalArgumentException("El lote devuelto no pertenece al medicamento indicado");
            }
            batch.setQuantityAvailable(batch.getQuantityAvailable() + item.getCantidad());
            batchRepository.save(batch);
            sincronizarInventario(item.getProductoId());
            movimientoRepository.save(MovimientoInventario.builder()
                    .productoId(item.getProductoId())
                    .tipo(MovimientoInventario.TipoMovimiento.DEVOLUCION)
                    .cantidad(item.getCantidad())
                    .referencia("Devolución " + request.getReturnNumber() + " (Lote " + batch.getBatchNumber() + ")")
                    .fecha(LocalDateTime.now())
                    .build());
        }
        operationRepository.save(InventoryOperation.builder()
                .operationKey(operationKey)
                .operationType("RETURN")
                .build());
    }

    private void sincronizarInventario(Long productoId) {
        int totalStock = batchRepository.findByMedicineIdForUpdate(productoId).stream()
                .mapToInt(b -> b.getQuantityAvailable() == null ? 0 : b.getQuantityAvailable()).sum();
        Inventario inventario = inventarioRepository.findByProductoIdForUpdate(productoId)
                .orElseGet(() -> Inventario.builder().productoId(productoId).stockActual(0).stockMinimo(5).build());
        inventario.setStockActual(totalStock);
        inventarioRepository.save(inventario);
    }

    @Transactional(readOnly = true)
    public List<MovimientoDTO> listarMovimientos(Long productoId) {
        List<MovimientoInventario> list;
        if (productoId != null) {
            list = movimientoRepository.findByProductoIdOrderByFechaDesc(productoId);
        } else {
            list = movimientoRepository.findTop50ByOrderByFechaDesc();
        }
        return list.stream().map(this::mapMovimientoToDTO).collect(Collectors.toList());
    }

    private InventarioDTO mapToDTO(Inventario i) {
        boolean bajo = i.getStockActual() != null && i.getStockMinimo() != null && i.getStockActual() <= i.getStockMinimo();
        return InventarioDTO.builder()
                .id(i.getId())
                .productoId(i.getProductoId())
                .stockActual(i.getStockActual())
                .stockMinimo(i.getStockMinimo())
                .stockBajo(bajo)
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private MovimientoDTO mapMovimientoToDTO(MovimientoInventario m) {
        return MovimientoDTO.builder()
                .id(m.getId())
                .productoId(m.getProductoId())
                .tipo(m.getTipo())
                .cantidad(m.getCantidad())
                .referencia(m.getReferencia())
                .fecha(m.getFecha())
                .build();
    }
}
