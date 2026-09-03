package com.medizano.cliente.repository;

import com.medizano.cliente.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByDocumento(String documento);
    List<Cliente> findByEstadoTrue();
    List<Cliente> findByNombreContainingIgnoreCase(String nombre);
}

