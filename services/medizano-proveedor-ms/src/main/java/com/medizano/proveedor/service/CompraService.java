package com.medizano.proveedor.service;

import com.medizano.proveedor.client.InventarioClient;
import com.medizano.proveedor.client.dto.RecepcionCompraStockRequest;
import com.medizano.proveedor.dto.CompraRequest;
import com.medizano.proveedor.dto.CompraResponse;
import com.medizano.proveedor.dto.RecepcionCompraRequest;
import com.medizano.proveedor.entity.Compra;
import com.medizano.proveedor.entity.Compra.EstadoCompra;
import com.medizano.proveedor.entity.DetalleCompra;
import com.medizano.proveedor.entity.Proveedor;
import com.medizano.proveedor.repository.CompraRepository;
import com.medizano.proveedor.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final InventarioClient inventarioClient;

    @Transactional
    public CompraResponse crearCompra(CompraRequest request) {
        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con ID: " + request.getProveedorId()));

        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new IllegalStateException("No se pueden registrar compras para un proveedor inactivo: " + proveedor.getRazonSocial());
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La compra debe incluir al menos un producto");
        }

        String numeroDoc = request.getNumeroDocumento();
        if (numeroDoc == null || numeroDoc.trim().isEmpty()) {
            numeroDoc = "COM-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + (new Random().nextInt(9000) + 1000);
        } else {
            numeroDoc = numeroDoc.trim();
            if (compraRepository.existsByNumeroDocumentoAndProveedorId(numeroDoc, proveedor.getId())) {
                throw new IllegalArgumentException("Ya existe una compra registrada con el documento " + numeroDoc + " para este proveedor");
            }
        }

        BigDecimal subtotalAcum = BigDecimal.ZERO;
        BigDecimal descuentoAcum = BigDecimal.ZERO;

        Compra compra = Compra.builder()
                .numeroDocumento(numeroDoc)
                .tipoDocumento(request.getTipoDocumento() != null ? request.getTipoDocumento() : Compra.TipoDocumento.FACTURA)
                .proveedor(proveedor)
                .fechaEmision(request.getFechaEmision() != null ? request.getFechaEmision() : LocalDate.now())
                .fechaRegistro(LocalDateTime.now())
                .estado(EstadoCompra.PENDIENTE)
                .observaciones(request.getObservaciones())
                .usuarioRegistro(request.getUsuarioRegistro() != null ? request.getUsuarioRegistro() : "Sistema")
                .detalles(new ArrayList<>())
                .build();

        for (CompraRequest.DetalleCompraRequest itemReq : request.getItems()) {
            if (itemReq.getCantidad() == null || itemReq.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero para el producto ID: " + itemReq.getProductoId());
            }
            if (itemReq.getCostoUnitario() == null || itemReq.getCostoUnitario().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El costo unitario no puede ser negativo para el producto ID: " + itemReq.getProductoId());
            }

            BigDecimal desc = itemReq.getDescuento() != null ? itemReq.getDescuento() : BigDecimal.ZERO;
            BigDecimal lineaBruta = itemReq.getCostoUnitario().multiply(new BigDecimal(itemReq.getCantidad()));
            BigDecimal lineaNeta = lineaBruta.subtract(desc).max(BigDecimal.ZERO);

            subtotalAcum = subtotalAcum.add(lineaNeta);
            descuentoAcum = descuentoAcum.add(desc);

            DetalleCompra detalle = DetalleCompra.builder()
                    .compra(compra)
                    .productoId(itemReq.getProductoId())
                    .nombreProducto(itemReq.getNombreProducto() != null ? itemReq.getNombreProducto() : "Medicamento #" + itemReq.getProductoId())
                    .cantidadComprada(itemReq.getCantidad())
                    .cantidadRecibida(0)
                    .costoUnitario(itemReq.getCostoUnitario())
                    .descuento(desc)
                    .subtotal(lineaNeta.setScale(2, RoundingMode.HALF_UP))
                    .loteSugerido(itemReq.getLoteSugerido())
                    .fechaVencimiento(itemReq.getFechaVencimiento())
                    .build();

            compra.getDetalles().add(detalle);
        }

        BigDecimal igvTasa = (request.getIgvPorcentaje() != null)
                ? request.getIgvPorcentaje().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : new BigDecimal("0.18");

        BigDecimal igvCalculado = subtotalAcum.multiply(igvTasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCalculado = subtotalAcum.add(igvCalculado).setScale(2, RoundingMode.HALF_UP);

        compra.setSubtotal(subtotalAcum.setScale(2, RoundingMode.HALF_UP));
        compra.setIgv(igvCalculado);
        compra.setDescuento(descuentoAcum.setScale(2, RoundingMode.HALF_UP));
        compra.setTotal(totalCalculado);

        Compra guardada = compraRepository.save(compra);
        log.info("Compra registrada exitosamente: ID {}, Documento {}, Total S/ {}", guardada.getId(), guardada.getNumeroDocumento(), guardada.getTotal());
        return mapToResponse(guardada);
    }

    @Transactional(readOnly = true)
    public Page<CompraResponse> listarCompras(Long proveedorId, EstadoCompra estado, LocalDate fechaInicio, LocalDate fechaFin, String termino, Pageable pageable) {
        String query = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        if (proveedorId == null && estado == null && fechaInicio == null && fechaFin == null && query == null) {
            return compraRepository.findAll(pageable).map(this::mapToResponse);
        }
        return compraRepository.findWithFilters(proveedorId, estado, fechaInicio, fechaFin, query, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CompraResponse obtenerPorId(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada con ID: " + id));
        return mapToResponse(compra);
    }

    @Transactional
    public CompraResponse actualizarEstado(Long id, EstadoCompra nuevoEstado, String observaciones) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada con ID: " + id));

        if (compra.getEstado() == EstadoCompra.RECIBIDA) {
            throw new IllegalStateException("La compra ya fue recibida e ingresada al inventario");
        }
        if (compra.getEstado() == EstadoCompra.CANCELADA) {
            throw new IllegalStateException("La compra se encuentra cancelada");
        }

        compra.setEstado(nuevoEstado);
        if (observaciones != null && !observaciones.isBlank()) {
            compra.setObservaciones((compra.getObservaciones() != null ? compra.getObservaciones() + " | " : "") + observaciones.trim());
        }
        Compra guardada = compraRepository.save(compra);
        log.info("Compra ID {} cambio estado a: {}", id, nuevoEstado);
        return mapToResponse(guardada);
    }

    @Transactional
    public CompraResponse cancelarCompra(Long id, String motivo) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada con ID: " + id));

        if (compra.getEstado() == EstadoCompra.RECIBIDA) {
            throw new IllegalStateException("No se puede cancelar una compra que ya fue recibida e ingresada al inventario");
        }
        if (compra.getEstado() == EstadoCompra.CANCELADA) {
            throw new IllegalStateException("La compra ya se encuentra cancelada");
        }

        compra.setEstado(EstadoCompra.CANCELADA);
        if (motivo != null && !motivo.isBlank()) {
            compra.setObservaciones((compra.getObservaciones() != null ? compra.getObservaciones() + " | " : "") + "Cancelada: " + motivo.trim());
        }
        Compra guardada = compraRepository.save(compra);
        log.info("Compra ID {} cancelada: {}", id, motivo);
        return mapToResponse(guardada);
    }

    @Transactional
    public CompraResponse confirmarRecepcion(Long id, RecepcionCompraRequest request) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada con ID: " + id));

        if (compra.getEstado() == EstadoCompra.RECIBIDA) {
            log.info("La compra ID {} ya fue recibida anteriormente; idempotencia respetada", id);
            return mapToResponse(compra);
        }

        if (compra.getEstado() == EstadoCompra.CANCELADA) {
            throw new IllegalStateException("No se puede recibir mercadería de una compra cancelada");
        }

        List<RecepcionCompraStockRequest.ItemRecepcionStock> itemsAEnviar = new ArrayList<>();

        for (DetalleCompra detalle : compra.getDetalles()) {
            // Buscar si viene especificación en el request
            RecepcionCompraRequest.ItemRecepcionDTO itemReq = null;
            if (request.getItems() != null) {
                itemReq = request.getItems().stream()
                        .filter(r -> r.getDetalleId() != null && r.getDetalleId().equals(detalle.getId()))
                        .findFirst()
                        .orElse(null);
            }

            int cantRecibida = (itemReq != null && itemReq.getCantidadRecibida() != null)
                    ? itemReq.getCantidadRecibida()
                    : detalle.getCantidadComprada();

            detalle.setCantidadRecibida(cantRecibida);

            String batchNumber = (itemReq != null && itemReq.getBatchNumber() != null && !itemReq.getBatchNumber().isBlank())
                    ? itemReq.getBatchNumber().trim()
                    : (detalle.getLoteSugerido() != null && !detalle.getLoteSugerido().isBlank())
                    ? detalle.getLoteSugerido().trim()
                    : "LOTE-" + compra.getNumeroDocumento() + "-" + detalle.getProductoId();

            LocalDate expiryDate = (itemReq != null && itemReq.getExpiryDate() != null)
                    ? itemReq.getExpiryDate()
                    : (detalle.getFechaVencimiento() != null)
                    ? detalle.getFechaVencimiento()
                    : LocalDate.now().plusMonths(24);

            detalle.setLoteSugerido(batchNumber);
            detalle.setFechaVencimiento(expiryDate);

            itemsAEnviar.add(RecepcionCompraStockRequest.ItemRecepcionStock.builder()
                    .productoId(detalle.getProductoId())
                    .productoNombre(detalle.getNombreProducto())
                    .batchNumber(batchNumber)
                    .expiryDate(expiryDate)
                    .cantidad(cantRecibida)
                    .purchasePrice(detalle.getCostoUnitario())
                    .build());
        }

        // Llamar a inventario-ms para dar ingreso físico y registrar lotes FEFO
        RecepcionCompraStockRequest stockRequest = RecepcionCompraStockRequest.builder()
                .numeroCompra(compra.getNumeroDocumento())
                .proveedorId(compra.getProveedor().getId())
                .proveedorNombre(compra.getProveedor().getRazonSocial())
                .usuarioResponsable(request.getUsuarioRecepcion() != null ? request.getUsuarioRecepcion() : "Almacenero")
                .items(itemsAEnviar)
                .build();

        try {
            inventarioClient.registrarRecepcionCompra(stockRequest);
            log.info("Mercadería de la compra {} comunicada e ingresada exitosamente a inventario-ms", compra.getNumeroDocumento());
        } catch (Exception ex) {
            log.error("Error al comunicar recepción a inventario-ms para compra {}: {}", compra.getNumeroDocumento(), ex.getMessage());
            throw new RuntimeException("Fallo al actualizar existencias en inventario: " + ex.getMessage(), ex);
        }

        compra.setEstado(EstadoCompra.RECIBIDA);
        compra.setFechaRecepcion(LocalDateTime.now());
        compra.setUsuarioRecepcion(request.getUsuarioRecepcion() != null ? request.getUsuarioRecepcion() : "Almacenero");
        if (request.getObservaciones() != null && !request.getObservaciones().isBlank()) {
            compra.setObservaciones((compra.getObservaciones() != null ? compra.getObservaciones() + " | " : "") + "Recepción: " + request.getObservaciones().trim());
        }

        Compra guardada = compraRepository.save(compra);
        log.info("Recepción de compra ID {} finalizada exitosamente.", guardada.getId());
        return mapToResponse(guardada);
    }

    private CompraResponse mapToResponse(Compra c) {
        List<CompraResponse.DetalleCompraResponse> detResponses = (c.getDetalles() == null) ? List.of() :
                c.getDetalles().stream().map(d -> CompraResponse.DetalleCompraResponse.builder()
                        .id(d.getId())
                        .productoId(d.getProductoId())
                        .nombreProducto(d.getNombreProducto())
                        .cantidadComprada(d.getCantidadComprada())
                        .cantidadRecibida(d.getCantidadRecibida())
                        .costoUnitario(d.getCostoUnitario())
                        .descuento(d.getDescuento())
                        .subtotal(d.getSubtotal())
                        .loteSugerido(d.getLoteSugerido())
                        .fechaVencimiento(d.getFechaVencimiento())
                        .build()).collect(Collectors.toList());

        return CompraResponse.builder()
                .id(c.getId())
                .numeroDocumento(c.getNumeroDocumento())
                .tipoDocumento(c.getTipoDocumento())
                .proveedorId(c.getProveedor() != null ? c.getProveedor().getId() : null)
                .proveedorRuc(c.getProveedor() != null ? c.getProveedor().getRuc() : null)
                .proveedorRazonSocial(c.getProveedor() != null ? c.getProveedor().getRazonSocial() : null)
                .proveedorTelefono(c.getProveedor() != null ? c.getProveedor().getTelefonoContacto() : null)
                .fechaEmision(c.getFechaEmision())
                .fechaRegistro(c.getFechaRegistro())
                .fechaRecepcion(c.getFechaRecepcion())
                .estado(c.getEstado())
                .subtotal(c.getSubtotal())
                .igv(c.getIgv())
                .descuento(c.getDescuento())
                .total(c.getTotal())
                .observaciones(c.getObservaciones())
                .usuarioRegistro(c.getUsuarioRegistro())
                .usuarioRecepcion(c.getUsuarioRecepcion())
                .totalItems(c.getDetalles() != null ? c.getDetalles().size() : 0)
                .detalles(detResponses)
                .build();
    }
}

