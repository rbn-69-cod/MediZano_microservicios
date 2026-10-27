package com.medizano.proveedor.service;

import com.medizano.proveedor.dto.ProveedorDTO;
import com.medizano.proveedor.entity.Proveedor;
import com.medizano.proveedor.repository.CompraRepository;
import com.medizano.proveedor.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final CompraRepository compraRepository;

    private static final Pattern RUC_PATTERN = Pattern.compile("^(10|15|17|20)\\d{9}$");

    @Transactional(readOnly = true)
    public Map<Long, ProveedorDTO> buscarPorIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return proveedorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        Proveedor::getId,
                        p -> ProveedorDTO.builder()
                                .id(p.getId())
                                .razonSocial(p.getRazonSocial())
                                .emailContacto(p.getEmailContacto())
                                .ruc(p.getRuc())
                                .build(),
                        (existing, replacement) -> existing
                ));
    }

    @Transactional(readOnly = true)
    public Page<Proveedor> listarPaginado(String filtro, Pageable pageable) {
        return buscar(filtro, false, pageable);
    }

    public List<Proveedor> listarTodos() {
        return proveedorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Proveedor> listarActivos() {
        return proveedorRepository.findByActivoTrue();
    }

    @Transactional(readOnly = true)
    public Page<Proveedor> buscar(String termino, boolean soloActivos, Pageable pageable) {
        String query = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        if (query == null) {
            return soloActivos ? proveedorRepository.findByActivoTrue(pageable) : proveedorRepository.findAll(pageable);
        }
        return proveedorRepository.buscarConFiltro(query, soloActivos, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Proveedor> buscarPorId(Long id) {
        return proveedorRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Proveedor> buscarPorRuc(String ruc) {
        return proveedorRepository.findByRuc(ruc);
    }

    @Transactional
    public Proveedor crear(Proveedor proveedor) {
        validarRucPeru(proveedor.getRuc());

        if (proveedorRepository.findByRuc(proveedor.getRuc().trim()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un proveedor registrado con el RUC: " + proveedor.getRuc());
        }

        proveedor.setRuc(proveedor.getRuc().trim());
        proveedor.setFechaRegistro(LocalDateTime.now());
        if (proveedor.getActivo() == null) {
            proveedor.setActivo(true);
        }
        log.info("Registrando nuevo proveedor RUC: {}, Razón Social: {}", proveedor.getRuc(), proveedor.getRazonSocial());
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public Proveedor actualizar(Long id, Proveedor datos) {
        Proveedor existente = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con ID: " + id));

        if (datos.getRuc() != null && !datos.getRuc().trim().equals(existente.getRuc())) {
            validarRucPeru(datos.getRuc());
            proveedorRepository.findByRuc(datos.getRuc().trim()).ifPresent(otro -> {
                if (!otro.getId().equals(id)) {
                    throw new IllegalArgumentException("Ya existe otro proveedor registrado con el RUC: " + datos.getRuc());
                }
            });
            existente.setRuc(datos.getRuc().trim());
        }

        if (datos.getRazonSocial() != null && !datos.getRazonSocial().isBlank()) {
            existente.setRazonSocial(datos.getRazonSocial().trim());
        }
        existente.setNombreComercial(datos.getNombreComercial());
        existente.setContactoResponsable(datos.getContactoResponsable());
        if (datos.getDireccionFiscal() != null) {
            existente.setDireccionFiscal(datos.getDireccionFiscal());
        }
        if (datos.getTelefonoContacto() != null) {
            existente.setTelefonoContacto(datos.getTelefonoContacto());
        }
        if (datos.getEmailContacto() != null) {
            existente.setEmailContacto(datos.getEmailContacto());
        }
        existente.setRegistroDigemid(datos.getRegistroDigemid());
        existente.setObservaciones(datos.getObservaciones());
        if (datos.getActivo() != null) {
            existente.setActivo(datos.getActivo());
        }
        existente.setFechaActualizacion(LocalDateTime.now());

        log.info("Actualizando proveedor ID {}: {}", id, existente.getRazonSocial());
        return proveedorRepository.save(existente);
    }

    @Transactional
    public void desactivar(Long id) {
        eliminarOdesactivar(id);
    }

    @Transactional
    public void eliminarOdesactivar(Long id) {
        Proveedor existente = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con ID: " + id));

        // Regla de integridad: Si tiene compras relacionadas, no se permite borrado físico
        boolean tieneCompras = compraRepository.existsByProveedorId(id);
        if (tieneCompras) {
            existente.setActivo(false);
            existente.setFechaActualizacion(LocalDateTime.now());
            proveedorRepository.save(existente);
            log.warn("Proveedor ID {} tiene órdenes de compra asociadas. Se aplicó desactivación lógica en vez de borrado físico.", id);
        } else {
            proveedorRepository.delete(existente);
            log.info("Proveedor ID {} sin compras históricas eliminado físicamente", id);
        }
    }

    private void validarRucPeru(String ruc) {
        if (ruc == null || !RUC_PATTERN.matcher(ruc.trim()).matches()) {
            throw new IllegalArgumentException("El RUC peruano ingresado es inválido ('" + ruc +
                    "'). Debe contener 11 dígitos numéricos y comenzar con 10, 15, 17 o 20.");
        }
    }
}
