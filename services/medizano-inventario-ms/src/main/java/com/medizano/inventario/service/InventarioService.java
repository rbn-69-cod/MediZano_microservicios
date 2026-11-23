package com.medizano.inventario.service;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.dto.RestockStockRequest;
import com.medizano.inventario.dto.RecepcionCompraStockRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.medizano.inventario.catalogo.entity.Medicine;
import com.medizano.inventario.catalogo.repository.MedicineRepository;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.entity.MovimientoInventario;
import com.medizano.inventario.entity.InventoryOperation;
import com.medizano.inventario.exception.LoteNoAptoVentaException;
import com.medizano.inventario.entity.StockBarcode;
import com.medizano.inventario.repository.StockBarcodeRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final BatchRepository batchRepository;
    private final MedicineRepository medicineRepository;
    private final StockBarcodeRepository stockBarcodeRepository;
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
                .referencia(referenciaCorta(request.getReferencia() != null ? request.getReferencia() : "Ingreso manual"))
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
                .referencia(referenciaCorta(request.getReferencia() != null ? request.getReferencia() : "Salida manual"))
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
                        .referencia(referenciaCorta("Venta " + request.getNumeroVenta() + " (Lote " + resultado.getBatchNumber() + ")"))
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
                            .referencia(referenciaCorta("Venta " + request.getNumeroVenta() + " (FEFO Lote " + despacho.getBatchNumber() + ")"))
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

    /**
     * Verifica que una venta pueda ser atendida sin modificar existencias. Se usa
     * antes de abrir/cobrar un checkout para no aceptar pagos que excedan el
     * stock disponible. El descuento final vuelve a validar dentro de su propia
     * transacción, por lo que nunca puede dejar el stock negativo.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void validarDisponibilidadVenta(DescuentoStockRequest request) {
        validarDisponibilidad(request);
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
                    .referencia(referenciaCorta("Devolución " + request.getReturnNumber() + " (Lote " + batch.getBatchNumber() + ")"))
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
        log.info("Procesando ingreso de mercaderia por compra: {}", request.getNumeroCompra());
        if (request.getNumeroCompra() == null || request.getNumeroCompra().isBlank()) {
            throw new IllegalArgumentException("El numero de compra es obligatorio");
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

            // La recepción admite pedidos creados con un ID histórico: crea o
            // recupera el producto real y luego mantiene un único lote activo
            // por producto para compartir stock, código de barras y precio.
            Medicine medicine = resolverOCrearProductoParaRecepcion(item, request);
            Long productoId = medicine.getId();
            Batch batch = batchRepository.findByMedicineIdForUpdate(productoId).stream()
                    .filter(b -> b.getEstado() == EstadoLote.DISPONIBLE && !b.isExpired())
                    .findFirst()
                    .orElseGet(() -> batchRepository.findByMedicineIdAndBatchNumber(productoId, item.getBatchNumber().trim()).orElse(null));

            if (batch != null) {
                batch.setQuantityAvailable((batch.getQuantityAvailable() != null ? batch.getQuantityAvailable() : 0) + item.getCantidad());
                if (item.getExpiryDate() != null && (batch.getExpiryDate() == null || item.getExpiryDate().isBefore(batch.getExpiryDate()))) {
                    batch.setExpiryDate(item.getExpiryDate());
                }
                // Mantener el mismo precio del producto/lote existente
                if (medicine != null && medicine.getSellingPrice() != null && medicine.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
                    batch.setSellingPrice(medicine.getSellingPrice());
                }
                if (medicine != null && medicine.getPurchasePrice() != null && medicine.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
                    batch.setPurchasePrice(medicine.getPurchasePrice());
                }
                batch = batchRepository.save(batch);
            } else {
                BigDecimal purchasePrice = (medicine != null && medicine.getPurchasePrice() != null && medicine.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0)
                        ? medicine.getPurchasePrice()
                        : (item.getPurchasePrice() != null ? item.getPurchasePrice() : BigDecimal.valueOf(1.00));
                BigDecimal sellingPrice = (medicine != null && medicine.getSellingPrice() != null && medicine.getSellingPrice().compareTo(BigDecimal.ZERO) > 0)
                        ? medicine.getSellingPrice()
                        : purchasePrice.multiply(BigDecimal.valueOf(1.30)).setScale(2, java.math.RoundingMode.HALF_UP);

                batch = Batch.builder()
                        .medicineId(productoId)
                        .medicineName(medicine.getName())
                        .batchNumber(item.getBatchNumber().trim())
                        .expiryDate(item.getExpiryDate() != null ? item.getExpiryDate() : LocalDate.now().plusYears(1))
                        .purchasePrice(purchasePrice)
                        .sellingPrice(sellingPrice)
                        .quantityAvailable(item.getCantidad())
                        .estado(EstadoLote.DISPONIBLE)
                        .proveedorId(request.getProveedorId())
                        .numeroFacturaCompra(request.getNumeroCompra())
                        .build();
                batch = batchRepository.save(batch);

                if (medicine != null) {
                    boolean changed = false;
                    if (medicine.getPurchasePrice() == null || medicine.getPurchasePrice().compareTo(BigDecimal.ZERO) <= 0) {
                        medicine.setPurchasePrice(purchasePrice);
                        changed = true;
                    }
                    if (medicine.getSellingPrice() == null || medicine.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
                        medicine.setSellingPrice(sellingPrice);
                        changed = true;
                    }
                    if (changed) {
                        medicineRepository.save(medicine);
                    }
                }
            }

            // Garantizar que el código de barras del producto apunte al lote unificado
            if (medicine != null && medicine.getBarcode() != null && !medicine.getBarcode().isBlank()) {
                String code = medicine.getBarcode().trim();
                if (stockBarcodeRepository.findByBarcode(code).isEmpty()) {
                    stockBarcodeRepository.save(StockBarcode.builder()
                            .batch(batch)
                            .barcode(code)
                            .sold(false)
                            .build());
                }
            }

            // El catálogo es la fuente que consume Ventas. Al recepcionar una compra,
            // sus precios deben reflejar el lote recién ingresado; de otro modo queda
            // stock vendible sin PVP y la orden de venta no se puede crear.
            sincronizarPreciosCatalogo(productoId, batch.getPurchasePrice(), batch.getSellingPrice());

            sincronizarInventario(productoId);

            String proveedorTxt = (request.getProveedorNombre() != null && !request.getProveedorNombre().isBlank())
                    ? " (" + request.getProveedorNombre() + ")" : "";
            MovimientoInventario mov = MovimientoInventario.builder()
                    .productoId(productoId)
                    .tipo(MovimientoInventario.TipoMovimiento.ENTRADA)
                    .cantidad(item.getCantidad())
                    .referencia(referenciaCorta("Recepcion Compra " + request.getNumeroCompra() + proveedorTxt + " [Lote: " + batch.getBatchNumber() + "]"))
                    .fecha(LocalDateTime.now())
                    .build();
            movimientoRepository.save(mov);
        }

        operationRepository.save(InventoryOperation.builder()
                .operationKey(operationKey)
                .operationType("PURCHASE")
                .build());

        log.info("Ingreso de mercaderia completado exitosamente para compra {}", request.getNumeroCompra());
    }

    private Medicine resolverOCrearProductoParaRecepcion(RecepcionCompraStockRequest.ItemRecepcionStock item,
                                                           RecepcionCompraStockRequest request) {
        return medicineRepository.findById(item.getProductoId()).orElseGet(() -> {
            String nombre = item.getProductoNombre() == null || item.getProductoNombre().isBlank()
                    ? "Producto recibido " + item.getProductoId()
                    : item.getProductoNombre().trim();

            // Evita duplicar el catálogo cuando el pedido contiene un ID viejo
            // pero el mismo producto ya fue dado de alta en esta instalación.
            return medicineRepository.findByNameContainingIgnoreCase(nombre).stream()
                    .filter(m -> m.getName().equalsIgnoreCase(nombre))
                    .findFirst()
                    .orElseGet(() -> {
                        BigDecimal compra = item.getPurchasePrice() != null && item.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0
                                ? item.getPurchasePrice() : BigDecimal.ONE;
                        BigDecimal venta = compra.multiply(BigDecimal.valueOf(1.30)).setScale(2, RoundingMode.HALF_UP);
                        String fabricante = request.getProveedorNombre() == null || request.getProveedorNombre().isBlank()
                                ? "Proveedor de compra" : request.getProveedorNombre().trim();
                        Medicine nuevo = Medicine.builder()
                                .name(nombre)
                                .manufacturer(fabricante)
                                .category("Compra a proveedor")
                                .hsnCode(("REC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)).toUpperCase())
                                .gstPercentage(new BigDecimal("18.00"))
                                .prescriptionRequired(false)
                                .status(Medicine.Status.ACTIVE)
                                .purchasePrice(compra)
                                .sellingPrice(venta)
                                .build();
                        Medicine creado = medicineRepository.save(nuevo);
                        log.info("Producto creado durante recepción: id={}, nombre={}", creado.getId(), creado.getName());
                        return creado;
                    });
        });
    }

    private void sincronizarInventario(Long productoId) {
        int totalStock = batchRepository.findByMedicineIdForUpdate(productoId).stream()
                .mapToInt(b -> b.getQuantityAvailable() == null ? 0 : b.getQuantityAvailable()).sum();
        Inventario inventario = inventarioRepository.findByProductoIdForUpdate(productoId)
                .orElseGet(() -> Inventario.builder().productoId(productoId).stockActual(0).stockMinimo(5).build());
        inventario.setStockActual(totalStock);
        inventarioRepository.save(inventario);
    }

    private void sincronizarPreciosCatalogo(Long productoId, BigDecimal precioCompra, BigDecimal precioVenta) {
        Medicine medicine = medicineRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto " + productoId + " en el catálogo"));

        BigDecimal compraValida = precioCompra != null && precioCompra.compareTo(BigDecimal.ZERO) > 0
                ? precioCompra : BigDecimal.ONE;
        BigDecimal ventaValida = precioVenta != null && precioVenta.compareTo(BigDecimal.ZERO) > 0
                ? precioVenta
                : compraValida.multiply(BigDecimal.valueOf(1.30)).setScale(2, RoundingMode.HALF_UP);

        medicine.setPurchasePrice(compraValida);
        medicine.setSellingPrice(ventaValida);
        medicineRepository.save(medicine);
    }

    private String referenciaCorta(String referencia) {
        if (referencia == null) return null;
        return referencia.length() <= 100 ? referencia : referencia.substring(0, 97) + "...";
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
