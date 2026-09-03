package com.medizano.orden.repository;

import com.medizano.orden.entity.Orden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {
    Optional<Orden> findByNumeroOrden(String numeroOrden);
    List<Orden> findByClienteIdOrderByFechaDesc(Long clienteId);
    List<Orden> findTop50ByOrderByFechaDesc();
}

