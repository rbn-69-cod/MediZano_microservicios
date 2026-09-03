package com.medizano.inventario.service;

import com.medizano.inventario.dto.DescuentoStockRequest;
import com.medizano.inventario.dto.InventarioDTO;
import com.medizano.inventario.dto.MovimientoDTO;
import com.medizano.inventario.dto.MovimientoRequest;
import com.medizano.inventario.entity.Inventario;
import com.medizano.inventario.entity.MovimientoInventario;
import com.medizano.inventario.repository.InventarioRepository;
import com.medizano.inventario.repository.MovimientoInventarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;

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
        Inventario inventario = inventarioRepository.findByProductoId(request.getProductoId())
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
        Inventario inventario = inventarioRepository.findByProductoId(request.getProductoId())
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

        for (DescuentoStockRequest.ItemDescuento item : request.getItems()) {
            Inventario inventario = inventarioRepository.findByProductoId(item.getProductoId())
                    .orElseThrow(() -> new RuntimeException("No existe inventario para el producto ID: " + item.getProductoId()));

            if (inventario.getStockActual() < item.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para el producto ID " + item.getProductoId() +
                        ". Stock disponible: " + inventario.getStockActual() + ", requerido: " + item.getCantidad());
            }

            inventario.setStockActual(inventario.getStockActual() - item.getCantidad());
            inventarioRepository.save(inventario);

            MovimientoInventario mov = MovimientoInventario.builder()
                    .productoId(item.getProductoId())
                    .tipo(MovimientoInventario.TipoMovimiento.SALIDA)
                    .cantidad(item.getCantidad())
                    .referencia("Venta " + request.getNumeroVenta())
                    .fecha(LocalDateTime.now())
                    .build();
            movimientoRepository.save(mov);
        }

        log.info("Descuento de stock completado exitosamente para venta {}", request.getNumeroVenta());
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

