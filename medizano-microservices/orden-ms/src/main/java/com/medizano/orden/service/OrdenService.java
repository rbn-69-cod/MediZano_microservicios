package com.medizano.orden.service;

import com.medizano.orden.client.CatalogoClient;
import com.medizano.orden.client.ClienteClient;
import com.medizano.orden.client.FacturacionClient;
import com.medizano.orden.client.InventarioClient;
import com.medizano.orden.dto.CrearOrdenRequest;
import com.medizano.orden.dto.DetalleOrdenDTO;
import com.medizano.orden.dto.OrdenDTO;
import com.medizano.orden.entity.DetalleOrden;
import com.medizano.orden.entity.Orden;
import com.medizano.orden.repository.OrdenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrdenService {

    private final OrdenRepository ordenRepository;
    private final CatalogoClient catalogoClient;
    private final ClienteClient clienteClient;
    private final InventarioClient inventarioClient;
    private final FacturacionClient facturacionClient;

    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    @Transactional(readOnly = true)
    public List<OrdenDTO> listarOrdenes() {
        return ordenRepository.findTop50ByOrderByFechaDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrdenDTO obtenerPorId(Long id) {
        Orden o = ordenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la orden con ID: " + id));
        return mapToDTO(o);
    }

    @Transactional(readOnly = true)
    public Orden obtenerEntidadPorId(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la orden con ID: " + id));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public OrdenDTO crearOrden(CrearOrdenRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La orden debe contener al menos un producto");
        }
        String paymentMethod = request.getMetodoPago() == null ? "" : request.getMetodoPago().trim().toUpperCase();
        if (!List.of("EFECTIVO", "CASH", "PAYPAL", "MERCADO_PAGO").contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago no soportado");
        }
        if ("CASH".equals(paymentMethod)) {
            paymentMethod = "EFECTIVO";
        }

        // Consultar datos de cliente si aplica
        String clienteNombre = request.getClienteNombre();
        String clienteEmail = request.getClienteEmail();
        if (request.getClienteId() != null) {
            try {
                ClienteClient.ClienteResponse c = clienteClient.obtenerClientePorId(request.getClienteId());
                if (c != null) {
                    clienteNombre = c.getNombre();
                    clienteEmail = c.getEmail();
                }
            } catch (Exception ex) {
                log.warn("No se pudo obtener datos del cliente {}: {}", request.getClienteId(), ex.getMessage());
            }
        }
        if (clienteNombre == null || clienteNombre.trim().isEmpty()) {
            clienteNombre = "Cliente General";
        }

        String numeroOrden = generarNumeroOrden();

        Orden orden = Orden.builder()
                .numeroOrden(numeroOrden)
                .fecha(LocalDateTime.now())
                .clienteId(request.getClienteId())
                .clienteNombre(clienteNombre)
                .clienteEmail(clienteEmail)
                .usuarioId(request.getUsuarioId() != null ? request.getUsuarioId() : 1L)
                .usuarioNombre(request.getUsuarioNombre() != null ? request.getUsuarioNombre() : "Cajero")
                .metodoPago(paymentMethod)
                .subtotal(BigDecimal.ZERO)
                .impuesto(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .estado(Orden.EstadoOrden.PENDING)
                .detalles(new ArrayList<>())
                .build();

        BigDecimal subtotalAcumulado = BigDecimal.ZERO;
        List<InventarioClient.ItemDescuento> itemsDescuento = new ArrayList<>();
        List<FacturacionClient.ItemFactura> itemsFactura = new ArrayList<>();

        for (CrearOrdenRequest.ItemOrdenRequest itemReq : request.getItems()) {
            if (itemReq.getCantidad() == null || itemReq.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
            }

            String prodNombre;
            BigDecimal precioVenta;

            try {
                CatalogoClient.ProductoResponse prod = catalogoClient.obtenerProductoPorId(itemReq.getProductoId());
                if (prod == null || prod.getPrecioVenta() == null || prod.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException("El producto " + itemReq.getProductoId() + " no tiene un precio de venta válido.");
                }
                prodNombre = prod.getNombre();
                precioVenta = prod.getPrecioVenta();
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo validar el producto " + itemReq.getProductoId()
                        + " con catalogo-ms. La venta no fue creada.", ex);
            }

            BigDecimal itemSubtotal = precioVenta.multiply(new BigDecimal(itemReq.getCantidad())).setScale(2, RoundingMode.HALF_UP);
            subtotalAcumulado = subtotalAcumulado.add(itemSubtotal);

            DetalleOrden detalle = DetalleOrden.builder()
                    .orden(orden)
                    .productoId(itemReq.getProductoId())
                    .productoNombre(prodNombre)
                    .cantidad(itemReq.getCantidad())
                    .precioUnitario(precioVenta)
                    .subtotal(itemSubtotal)
                    .build();

            orden.getDetalles().add(detalle);
            itemsDescuento.add(new InventarioClient.ItemDescuento(itemReq.getProductoId(), itemReq.getCantidad()));
            itemsFactura.add(new FacturacionClient.ItemFactura(itemReq.getProductoId(), prodNombre, itemReq.getCantidad(), precioVenta, itemSubtotal));
        }

        BigDecimal total = subtotalAcumulado.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotalNeto = total.divide(BigDecimal.ONE.add(IGV_RATE), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotalNeto).setScale(2, RoundingMode.HALF_UP);

        orden.setSubtotal(subtotalNeto);
        orden.setImpuesto(igv);
        orden.setTotal(total);

        // Si es pago en EFECTIVO -> Marca PAGADA, descuenta stock y genera factura
        if ("EFECTIVO".equalsIgnoreCase(orden.getMetodoPago())) {
            orden.setEstado(Orden.EstadoOrden.PAGADA);
            orden.setReferenciaPago("CASH-" + System.currentTimeMillis());
            orden = ordenRepository.save(orden);

            procesarPostPago(orden, itemsDescuento, itemsFactura, false);
        } else {
            // Si es PAYPAL -> Queda en estado PENDING, stock permanece intacto
            orden.setEstado(Orden.EstadoOrden.PENDING);
            orden = ordenRepository.save(orden);
            log.info("Orden pendiente creada para pago con PayPal: {} (Stock intacto)", orden.getNumeroOrden());
        }

        return mapToDTO(orden);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class,
            noRollbackFor = IllegalStateException.class)
    public OrdenDTO confirmarPagoOrden(Long ordenId, String referenciaPago) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new RuntimeException("No se encontró la orden con ID: " + ordenId));

        if (orden.getEstado() == Orden.EstadoOrden.CANCELADA) {
            throw new IllegalStateException("La orden está cancelada y no puede ser pagada");
        }

        if (referenciaPago == null || referenciaPago.isBlank()) {
            throw new IllegalArgumentException("La referencia verificada del pago es obligatoria");
        }
        if (orden.getEstado() == Orden.EstadoOrden.PAGADA
                && orden.getReferenciaPago() != null
                && !orden.getReferenciaPago().equals(referenciaPago)) {
            throw new IllegalStateException("La orden ya tiene una referencia de pago diferente");
        }
        if (orden.getEstado() == Orden.EstadoOrden.PAGADA
                && !Boolean.FALSE.equals(orden.getInventarioProcesado())
                && !Boolean.FALSE.equals(orden.getFacturaGenerada())) {
            log.info("Orden {} ya fue procesada completamente. Respuesta idempotente.", orden.getNumeroOrden());
            return mapToDTO(orden);
        }

        orden.setEstado(Orden.EstadoOrden.PAGADA);
        if (orden.getReferenciaPago() == null) {
            orden.setReferenciaPago(referenciaPago);
        }
        orden = ordenRepository.save(orden);

        // Descontar stock atómicamente en inventario-ms
        List<InventarioClient.ItemDescuento> itemsDescuento = orden.getDetalles().stream()
                .map(d -> new InventarioClient.ItemDescuento(d.getProductoId(), d.getCantidad()))
                .collect(Collectors.toList());

        List<FacturacionClient.ItemFactura> itemsFactura = orden.getDetalles().stream()
                .map(d -> new FacturacionClient.ItemFactura(d.getProductoId(), d.getProductoNombre(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()))
                .collect(Collectors.toList());

        procesarPostPago(orden, itemsDescuento, itemsFactura, true);

        return mapToDTO(orden);
    }

    private void procesarPostPago(Orden orden, List<InventarioClient.ItemDescuento> itemsDescuento,
                                  List<FacturacionClient.ItemFactura> itemsFactura, boolean strict) {
        try {
            if (!Boolean.TRUE.equals(orden.getInventarioProcesado())) {
                inventarioClient.descontarStockVenta(
                        new InventarioClient.DescuentoStockRequest(orden.getNumeroOrden(), itemsDescuento));
                orden.setInventarioProcesado(true);
                orden.setErrorProcesamiento(null);
                ordenRepository.save(orden);
            }

            if (!Boolean.TRUE.equals(orden.getFacturaGenerada())) {
                facturacionClient.generarFactura(FacturacionClient.GenerarFacturaRequest.builder()
                        .ordenId(orden.getId())
                        .numeroOrden(orden.getNumeroOrden())
                        .clienteId(orden.getClienteId())
                        .clienteNombre(orden.getClienteNombre())
                        .metodoPago(orden.getMetodoPago())
                        .referenciaPago(orden.getReferenciaPago())
                        .subtotal(orden.getSubtotal())
                        .impuesto(orden.getImpuesto())
                        .total(orden.getTotal())
                        .items(itemsFactura)
                        .build());
                orden.setFacturaGenerada(true);
                orden.setErrorProcesamiento(null);
                ordenRepository.save(orden);
            }
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            orden.setErrorProcesamiento(message.substring(0, Math.min(message.length(), 500)));
            ordenRepository.save(orden);
            log.error("Orden {} pagada pero con procesamiento pendiente: {}", orden.getNumeroOrden(), message);
            if (strict) {
                throw new IllegalStateException("Pago registrado; la orden quedó pendiente de completar: " + message, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${orders.processing-retry-delay-ms:30000}")
    @Transactional
    public void reintentarOrdenesPagadasPendientes() {
        ordenRepository.findPaidPendingProcessing(Orden.EstadoOrden.PAGADA).stream().limit(50).forEach(orden -> {
            List<InventarioClient.ItemDescuento> stockItems = orden.getDetalles().stream()
                    .map(d -> new InventarioClient.ItemDescuento(d.getProductoId(), d.getCantidad()))
                    .collect(Collectors.toList());
            List<FacturacionClient.ItemFactura> invoiceItems = orden.getDetalles().stream()
                    .map(d -> new FacturacionClient.ItemFactura(d.getProductoId(), d.getProductoNombre(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()))
                    .collect(Collectors.toList());
            procesarPostPago(orden, stockItems, invoiceItems, false);
        });
    }

    @Transactional
    public OrdenDTO cancelarOrden(Long ordenId) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new RuntimeException("No se encontró la orden con ID: " + ordenId));

        if (orden.getEstado() == Orden.EstadoOrden.PAGADA) {
            throw new IllegalStateException("No se puede cancelar directamente una orden ya pagada.");
        }

        orden.setEstado(Orden.EstadoOrden.CANCELADA);
        orden = ordenRepository.save(orden);
        log.info("Orden {} cancelada", orden.getNumeroOrden());
        return mapToDTO(orden);
    }

    private String generarNumeroOrden() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = new Random().nextInt(900) + 100;
        return "ORD-" + timestamp + "-" + rand;
    }

    private OrdenDTO mapToDTO(Orden o) {
        List<DetalleOrdenDTO> detallesDTO = o.getDetalles().stream()
                .map(d -> DetalleOrdenDTO.builder()
                        .id(d.getId())
                        .productoId(d.getProductoId())
                        .productoNombre(d.getProductoNombre())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrdenDTO.builder()
                .id(o.getId())
                .numeroOrden(o.getNumeroOrden())
                .fecha(o.getFecha())
                .clienteId(o.getClienteId())
                .clienteNombre(o.getClienteNombre())
                .clienteEmail(o.getClienteEmail())
                .usuarioId(o.getUsuarioId())
                .usuarioNombre(o.getUsuarioNombre())
                .subtotal(o.getSubtotal())
                .impuesto(o.getImpuesto())
                .total(o.getTotal())
                .estado(o.getEstado())
                .metodoPago(o.getMetodoPago())
                .referenciaPago(o.getReferenciaPago())
                .detalles(detallesDTO)
                .createdAt(o.getCreatedAt())
                .build();
    }
}
