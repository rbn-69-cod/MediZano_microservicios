package com.medizano.inventario.service;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.dto.RestockStockRequest;
import com.medizano.inventario.dto.RecepcionCompraStockRequest;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.entity.MovimientoInventario;
import com.medizano.inventario.entity.InventoryOperation;
import com.medizano.inventario.exception.LoteNoAptoVentaException;
import com.medizano.inventario.repository.BatchRepository;
import com.medizano.inventario.repository.InventarioRepository;
import com.medizano.inventario.repository.MovimientoInventarioRepository;
import com.medizano.inventario.repository.InventoryOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final FefoDispatchService fefoDispatchService;

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
                // Despacho de lote específico con guard de bloqueo (<= 25 días o cuarentena)
                FefoDispatchService.DespachoLoteResultado resultado = fefoDispatchService
                        .despacharLoteEspecifico(item.getProductoId(), item.getBatchId(), item.getCantidad());

                sincronizarInventario(item.getProductoId());

                MovimientoInventario mov = MovimientoInventario.builder()
                        .productoId(item.getProductoId())
                        .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                        .cantidad(item.getCantidad())
                        .referencia("Venta " + request.getNumeroVenta() + " (Lote " + resultado.getBatchNumber() + ")")
                        .fecha(LocalDateTime.now())
                        .build();
                movimientoRepository.save(mov);
            } else {
                // Despacho automatizado por algoritmo FEFO estricto
                List<FefoDispatchService.DespachoLoteResultado> despachos = fefoDispatchService
                        .despacharPorFefo(item.getProductoId(), item.getCantidad());

                sincronizarInventario(item.getProductoId());

                for (FefoDispatchService.DespachoLoteResultado despacho : despachos) {
                    MovimientoInventario mov = MovimientoInventario.builder()
                            .productoId(item.getProductoId())
                            .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                            .cantidad(despacho.getCantidadDescontada())
                            .referencia("Venta " + request.getNumeroVenta() + " (FEFO Lote " + despacho.getBatchNumber() + ")")
                            .fecha(LocalDateTime.now())
                            .build();
                    movimientoRepository.save(mov);
                }
            }
        }

        operationRepository.save(InventoryOperation.builder()
                .operationKey(operationKey)
                .operationType("SALE")
                .build());

        log.info("Descuento de stock completado exitosamente para venta {}", request.getNumeroVenta());
    }

    private void validarDisponibilidad(DescuentoStockRequest request) {
        LocalDate fechaLimiteSeguridad = LocalDate.now().plusDays(FefoDispatchService.DIAS_BLOQUEO_VENTA);

        for (DescuentoStockRequest.ItemDescuento item : request.getItems()) {
            if (item.getProductoId() == null || item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new IllegalArgumentException("Producto y cantidad positiva son obligatorios");
            }
            if (item.getBatchId() != null) {
                Batch batch = batchRepository.findByIdForUpdate(item.getBatchId())
                        .orElseThrow(() -> new RuntimeException("Lote no encontrado con ID: " + item.getBatchId()));
                if (!batch.getMedicineId().equals(item.getProductoId())) {
                    throw new IllegalArgumentException("El lote indicado no corresponde al producto");
                }
                boolean bloqueado = batch.getEstado() == EstadoLote.RETIRAR_MOSTRADOR
                        || batch.getEstado() == EstadoLote.VENCIDO_BAJA
                        || batch.getEstado() == EstadoLote.DEVUELTO
                        || batch.getExpiryDate() == null
                        || !batch.getExpiryDate().isAfter(fechaLimiteSeguridad);

                if (bloqueado) {
                    throw new LoteNoAptoVentaException(
                            "Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica (<= 25 días) o en cuarentena. Trasladar producto a área de bajas."
                    );
                }
                if (batch.getQuantityAvailable() < item.getCantidad()) {
                    throw new IllegalStateException("Stock insuficiente en el lote " + batch.getBatchNumber());
                }
            } else {
                List<Batch> aptos = batchRepository.findFefoEligibleBatchesForUpdate(item.getProductoId(), fechaLimiteSeguridad);
                int stockApto = aptos.stream().mapToInt(b -> b.getQuantityAvailable() == null ? 0 : b.getQuantityAvailable()).sum();

                if (stockApto < item.getCantidad()) {
                    List<Batch> todos = batchRepository.findByMedicineIdForUpdate(item.getProductoId());
                    int totalFisico = todos.stream().mapToInt(b -> b.getQuantityAvailable() == null ? 0 : b.getQuantityAvailable()).sum();
                    if (totalFisico > 0 && stockApto == 0) {
                        throw new LoteNoAptoVentaException(
                                "Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica (<= 25 días) o en cuarentena. Trasladar producto a área de bajas."
                        );
                    }
                    throw new IllegalStateException("Stock apto insuficiente para el producto ID " + item.getProductoId());
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

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void registrarRecepcionCompra(RecepcionCompraStockRequest request) {
        log.info("Procesando ingreso de mercadería por compra: {}", request.getNumeroCompra());
        if (request.getNumeroCompra() == null || request.getNumeroCompra().isBlank()) {
            throw new IllegalArgumentException("El número de compra es obligatorio");
        }
        String operationKey = "PURCHASE:" + request.getNumeroCompra().trim();
        if (operationRepository.existsByOperationKey(operationKey)) {
            log.info("Compra {} ya fue procesada previamente; idempotencia garantizada", operationKey);
            return;
        }

        for (RecepcionCompraStockRequest.ItemRecepcionStock item : request.getItems()) {
            if (item.getProductoId() == null || item.getCantidad() == null || item.getCantidad() <= 0) {
                continue;
            }

            Batch batch = batchRepository.findByMedicineIdAndBatchNumber(item.getProductoId(), item.getBatchNumber().trim())
                    .orElse(null);

            if (batch != null) {
                batch.setQuantityAvailable(batch.getQuantityAvailable() + item.getCantidad());
                if (item.getExpiryDate() != null) {
                    batch.setExpiryDate(item.getExpiryDate());
                }
                if (item.getPurchasePrice() != null) {
                    batch.setPurchasePrice(item.getPurchasePrice());
                }
                batch = batchRepository.save(batch);
            } else {
                BigDecimal purchasePrice = item.getPurchasePrice() != null ? item.getPurchasePrice() : BigDecimal.valueOf(1.00);
                BigDecimal defaultSellingPrice = purchasePrice.multiply(BigDecimal.valueOf(1.30)).setScale(2, java.math.RoundingMode.HALF_UP);
                batch = Batch.builder()
                        .medicineId(item.getProductoId())
                        .medicineName(item.getProductoNombre() != null ? item.getProductoNombre() : "Medicamento #" + item.getProductoId())
                        .batchNumber(item.getBatchNumber().trim())
                        .expiryDate(item.getExpiryDate() != null ? item.getExpiryDate() : LocalDate.now().plusYears(1))
                        .purchasePrice(purchasePrice)
                        .sellingPrice(defaultSellingPrice)
                        .quantityAvailable(item.getCantidad())
                        .build();
                batch = batchRepository.save(batch);
            }

            sincronizarInventario(item.getProductoId());

            String proveedorTxt = (request.getProveedorNombre() != null && !request.getProveedorNombre().isBlank())
                    ? " (" + request.getProveedorNombre() + ")" : "";
            MovimientoInventario mov = MovimientoInventario.builder()
                    .productoId(item.getProductoId())
                    .tipo(MovimientoInventario.TipoMovimiento.ENTRADA)
                    .cantidad(item.getCantidad())
                    .referencia("Recepción Compra " + request.getNumeroCompra() + proveedorTxt + " [Lote: " + batch.getBatchNumber() + "]")
                    .fecha(LocalDateTime.now())
                    .build();
            movimientoRepository.save(mov);
        }

        operationRepository.save(InventoryOperation.builder()
                .operationKey(operationKey)
                .operationType("PURCHASE")
                .build());

        log.info("Ingreso de mercadería completado exitosamente para compra {}", request.getNumeroCompra());
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
