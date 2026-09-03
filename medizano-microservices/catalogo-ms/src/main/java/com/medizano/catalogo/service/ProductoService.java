package com.medizano.catalogo.service;

import com.medizano.catalogo.dto.ProductoDTO;
import com.medizano.catalogo.dto.ProductoRequest;
import com.medizano.catalogo.entity.Producto;
import com.medizano.catalogo.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<ProductoDTO> listarTodos() {
        return productoRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoDTO> listarActivos() {
        return productoRepository.findByEstadoTrue().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductoDTO buscarPorId(Long id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));
        return mapToDTO(p);
    }

    @Transactional(readOnly = true)
    public ProductoDTO buscarPorCodigo(String codigo) {
        Producto p = productoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con código: " + codigo));
        return mapToDTO(p);
    }

    @Transactional
    public ProductoDTO crear(ProductoRequest request) {
        if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        Producto producto = new Producto();
        producto.setNombre(request.getNombre().trim());
        producto.setCodigo(request.getCodigo() != null ? request.getCodigo().trim() : null);
        producto.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        producto.setPrecioVenta(request.getPrecioVenta());
        producto.setPrecioCompra(request.getPrecioCompra());
        producto.setEstado(request.getEstado() != null ? request.getEstado() : true);
        producto.setCategoriaId(request.getCategoriaId());

        producto = productoRepository.save(producto);
        log.info("Producto creado en catálogo: id={}, nombre={}", producto.getId(), producto.getNombre());
        return mapToDTO(producto);
    }

    @Transactional
    public ProductoDTO actualizar(Long id, ProductoRequest request) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));

        if (request.getNombre() != null && !request.getNombre().trim().isEmpty()) {
            producto.setNombre(request.getNombre().trim());
        }
        if (request.getCodigo() != null) {
            producto.setCodigo(request.getCodigo().trim());
        }
        if (request.getDescripcion() != null) {
            producto.setDescripcion(request.getDescripcion().trim());
        }
        if (request.getPrecioVenta() != null) {
            producto.setPrecioVenta(request.getPrecioVenta());
        }
        if (request.getPrecioCompra() != null) {
            producto.setPrecioCompra(request.getPrecioCompra());
        }
        if (request.getEstado() != null) {
            producto.setEstado(request.getEstado());
        }
        if (request.getCategoriaId() != null) {
            producto.setCategoriaId(request.getCategoriaId());
        }

        producto = productoRepository.save(producto);
        log.info("Producto actualizado en catálogo: id={}", producto.getId());
        return mapToDTO(producto);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));
        p.setEstado(activo);
        productoRepository.save(p);
        log.info("Estado de producto id={} cambiado a: {}", id, activo);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new RuntimeException("No se encontró el producto con ID: " + id);
        }
        productoRepository.deleteById(id);
        log.info("Producto eliminado de catálogo: id={}", id);
    }

    private ProductoDTO mapToDTO(Producto p) {
        return new ProductoDTO(
                p.getId(),
                p.getNombre(),
                p.getCodigo(),
                p.getDescripcion(),
                p.getPrecioVenta(),
                p.getPrecioCompra(),
                p.getEstado(),
                p.getCategoriaId(),
                p.getCreatedAt()
        );
    }
}

