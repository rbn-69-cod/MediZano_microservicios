package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.FacturaDTO;
import com.medizano.facturacion.dto.GenerarFacturaRequest;
import com.medizano.facturacion.dto.ItemFacturaDTO;
import com.medizano.facturacion.entity.Factura;
import com.medizano.facturacion.entity.ItemFactura;
import com.medizano.facturacion.repository.FacturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturacionService {

    private final FacturaRepository facturaRepository;

    @Transactional(readOnly = true)
    public List<FacturaDTO> listarFacturas() {
        return facturaRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FacturaDTO obtenerPorId(Long id) {
        Factura f = facturaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la factura con ID: " + id));
        return mapToDTO(f);
    }

    @Transactional(readOnly = true)
    public Factura obtenerEntidadPorId(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la factura con ID: " + id));
    }

    @Transactional(readOnly = true)
    public FacturaDTO obtenerPorOrdenId(Long ordenId) {
        Factura f = facturaRepository.findByOrdenId(ordenId)
                .orElseThrow(() -> new RuntimeException("No se encontró la factura para la orden ID: " + ordenId));
        return mapToDTO(f);
    }

    @Transactional(readOnly = true)
    public Factura obtenerEntidadPorOrdenId(Long ordenId) {
        return facturaRepository.findByOrdenId(ordenId)
                .orElseThrow(() -> new RuntimeException("No se encontró la factura para la orden ID: " + ordenId));
    }

    @Transactional
    public FacturaDTO generarFactura(GenerarFacturaRequest request) {
        // Idempotencia: Si ya existe factura para esta orden, devolverla
        return facturaRepository.findByOrdenId(request.getOrdenId())
                .map(this::mapToDTO)
                .orElseGet(() -> {
                    String numeroFactura = "F001-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + (new Random().nextInt(900) + 100);

                    Factura factura = Factura.builder()
                            .numeroFactura(numeroFactura)
                            .ordenId(request.getOrdenId())
                            .numeroOrden(request.getNumeroOrden())
                            .clienteId(request.getClienteId())
                            .clienteNombre(request.getClienteNombre())
                            .metodoPago(request.getMetodoPago())
                            .referenciaPago(request.getReferenciaPago())
                            .subtotal(request.getSubtotal())
                            .impuesto(request.getImpuesto())
                            .total(request.getTotal())
                            .estado("EMITIDA")
                            .items(new ArrayList<>())
                            .createdAt(LocalDateTime.now())
                            .build();

                    if (request.getItems() != null) {
                        for (GenerarFacturaRequest.ItemFacturaRequest itemReq : request.getItems()) {
                            ItemFactura item = ItemFactura.builder()
                                    .factura(factura)
                                    .productoId(itemReq.getProductoId())
                                    .productoNombre(itemReq.getProductoNombre())
                                    .cantidad(itemReq.getCantidad())
                                    .precioUnitario(itemReq.getPrecioUnitario())
                                    .subtotal(itemReq.getSubtotal())
                                    .build();
                            factura.getItems().add(item);
                        }
                    }

                    Factura saved = facturaRepository.save(factura);
                    log.info("Factura electrónica emitida exitosamente: {} para orden {}", numeroFactura, request.getOrdenId());
                    return mapToDTO(saved);
                });
    }

    private FacturaDTO mapToDTO(Factura f) {
        List<ItemFacturaDTO> items = f.getItems().stream()
                .map(i -> ItemFacturaDTO.builder()
                        .id(i.getId())
                        .productoId(i.getProductoId())
                        .productoNombre(i.getProductoNombre())
                        .cantidad(i.getCantidad())
                        .precioUnitario(i.getPrecioUnitario())
                        .subtotal(i.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return FacturaDTO.builder()
                .id(f.getId())
                .numeroFactura(f.getNumeroFactura())
                .ordenId(f.getOrdenId())
                .numeroOrden(f.getNumeroOrden())
                .clienteId(f.getClienteId())
                .clienteNombre(f.getClienteNombre())
                .metodoPago(f.getMetodoPago())
                .referenciaPago(f.getReferenciaPago())
                .subtotal(f.getSubtotal())
                .impuesto(f.getImpuesto())
                .total(f.getTotal())
                .estado(f.getEstado())
                .items(items)
                .createdAt(f.getCreatedAt())
                .build();
    }
}

