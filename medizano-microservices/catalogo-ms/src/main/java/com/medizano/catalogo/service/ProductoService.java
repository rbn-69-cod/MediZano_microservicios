package com.medizano.catalogo.service;

import com.medizano.catalogo.dto.ProductoDTO;
import com.medizano.catalogo.dto.ProductoRequest;
import com.medizano.catalogo.entity.Medicine;
import com.medizano.catalogo.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoService {

    private final MedicineRepository medicineRepository;

    @Transactional(readOnly = true)
    public List<ProductoDTO> listarTodos() {
        return medicineRepository.findAll().stream()
                .map(this::mapMedicineToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoDTO> listarActivos() {
        return medicineRepository.findByStatus(Medicine.Status.ACTIVE).stream()
                .map(this::mapMedicineToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductoDTO buscarPorId(Long id) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));
        return mapMedicineToDTO(m);
    }

    @Transactional(readOnly = true)
    public ProductoDTO buscarPorCodigo(String codigo) {
        Medicine m = medicineRepository.findByBarcode(codigo)
                .or(() -> medicineRepository.findByHsnCode(codigo))
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con código: " + codigo));
        return mapMedicineToDTO(m);
    }

    @Transactional
    public ProductoDTO crear(ProductoRequest request) {
        if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        String barcode = request.getCodigo() != null ? request.getCodigo().trim() : null;
        String hsnCode = barcode != null ? barcode : "MED-" + System.currentTimeMillis();

        Medicine m = Medicine.builder()
                .name(request.getNombre().trim())
                .manufacturer("MediZano")
                .category("General")
                .barcode(barcode)
                .hsnCode(hsnCode)
                .gstPercentage(new BigDecimal("18.00"))
                .prescriptionRequired(false)
                .status(Boolean.FALSE.equals(request.getEstado()) ? Medicine.Status.DISCONTINUED : Medicine.Status.ACTIVE)
                .sellingPrice(request.getPrecioVenta())
                .purchasePrice(request.getPrecioCompra())
                .build();

        m = medicineRepository.save(m);
        log.info("Producto unificado creado en catálogo (Medicine): id={}, name={}", m.getId(), m.getName());
        return mapMedicineToDTO(m);
    }

    @Transactional
    public ProductoDTO actualizar(Long id, ProductoRequest request) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));

        if (request.getNombre() != null && !request.getNombre().trim().isEmpty()) {
            m.setName(request.getNombre().trim());
        }
        if (request.getCodigo() != null) {
            m.setBarcode(request.getCodigo().trim());
        }
        if (request.getPrecioVenta() != null) {
            m.setSellingPrice(request.getPrecioVenta());
        }
        if (request.getPrecioCompra() != null) {
            m.setPurchasePrice(request.getPrecioCompra());
        }
        if (request.getEstado() != null) {
            m.setStatus(request.getEstado() ? Medicine.Status.ACTIVE : Medicine.Status.DISCONTINUED);
        }

        m = medicineRepository.save(m);
        log.info("Producto unificado actualizado en catálogo: id={}", m.getId());
        return mapMedicineToDTO(m);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Medicine m = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con ID: " + id));
        m.setStatus(activo ? Medicine.Status.ACTIVE : Medicine.Status.DISCONTINUED);
        medicineRepository.save(m);
        log.info("Estado de producto id={} cambiado a: {}", id, activo);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!medicineRepository.existsById(id)) {
            throw new RuntimeException("No se encontró el producto con ID: " + id);
        }
        medicineRepository.deleteById(id);
        log.info("Producto eliminado de catálogo: id={}", id);
    }

    private ProductoDTO mapMedicineToDTO(Medicine m) {
        return new ProductoDTO(
                m.getId(),
                m.getName(),
                m.getBarcode() != null ? m.getBarcode() : m.getHsnCode(),
                m.getManufacturer() + (m.getCategory() != null ? " - " + m.getCategory() : ""),
                m.getSellingPrice(),
                m.getPurchasePrice(),
                m.getStatus() == Medicine.Status.ACTIVE,
                1L,
                m.getCreatedAt()
        );
    }
}

