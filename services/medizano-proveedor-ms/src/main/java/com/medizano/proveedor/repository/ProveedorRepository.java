package com.medizano.proveedor.repository;

import com.medizano.proveedor.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    Optional<Proveedor> findByRuc(String ruc);
    List<Proveedor> findByActivoTrue();
    Page<Proveedor> findByActivoTrue(Pageable pageable);

    @Query("SELECT p FROM Proveedor p WHERE " +
           "(:soloActivos = false OR p.activo = true) AND " +
           "(:filtro IS NULL OR :filtro = '' OR " +
           "LOWER(p.razonSocial) LIKE LOWER(CONCAT('%', :filtro, '%')) OR " +
           "LOWER(p.ruc) LIKE LOWER(CONCAT('%', :filtro, '%')) OR " +
           "LOWER(COALESCE(p.nombreComercial, '')) LIKE LOWER(CONCAT('%', :filtro, '%')))")
    Page<Proveedor> buscarConFiltro(@Param("filtro") String filtro,
                                    @Param("soloActivos") boolean soloActivos,
                                    Pageable pageable);
}
