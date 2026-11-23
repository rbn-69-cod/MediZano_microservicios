package com.medizano.proveedor.repository;

import com.medizano.proveedor.entity.Compra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByNumeroDocumento(String numeroDocumento);

    boolean existsByProveedorId(Long proveedorId);

    boolean existsByNumeroDocumentoAndProveedorId(String numeroDocumento, Long proveedorId);

    List<Compra> findByProveedorIdOrderByFechaRegistroDesc(Long proveedorId);

    Page<Compra> findByEstadoInOrderByFechaRegistroDesc(List<Compra.EstadoCompra> estados, Pageable pageable);

    @Query("SELECT c FROM Compra c WHERE " +
           "(:proveedorId IS NULL OR c.proveedor.id = :proveedorId) AND " +
           "(:estado IS NULL OR c.estado = :estado) AND " +
           "(:fechaInicio IS NULL OR c.fechaEmision >= :fechaInicio) AND " +
           "(:fechaFin IS NULL OR c.fechaEmision <= :fechaFin) AND " +
           "(CAST(:termino AS string) IS NULL OR " +
           " LOWER(c.numeroDocumento) LIKE LOWER(CONCAT('%', CAST(:termino AS string), '%')) OR " +
           " LOWER(c.proveedor.razonSocial) LIKE LOWER(CONCAT('%', CAST(:termino AS string), '%')) OR " +
           " LOWER(c.proveedor.ruc) LIKE LOWER(CONCAT('%', CAST(:termino AS string), '%'))) " +
           "ORDER BY c.fechaRegistro DESC")
    Page<Compra> findWithFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("estado") Compra.EstadoCompra estado,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("termino") String termino,
            Pageable pageable);
}

