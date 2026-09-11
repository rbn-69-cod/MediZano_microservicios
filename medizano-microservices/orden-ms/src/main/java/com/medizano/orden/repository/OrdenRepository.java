package com.medizano.orden.repository;

import com.medizano.orden.entity.Orden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {
    Optional<Orden> findByNumeroOrden(String numeroOrden);
    List<Orden> findByClienteIdOrderByFechaDesc(Long clienteId);
    List<Orden> findTop50ByOrderByFechaDesc();

    @Query("SELECT o FROM Orden o WHERE o.estado = :estado " +
            "AND (o.inventarioProcesado = false OR o.facturaGenerada = false) ORDER BY o.updatedAt ASC")
    List<Orden> findPaidPendingProcessing(@Param("estado") Orden.EstadoOrden estado);
}
