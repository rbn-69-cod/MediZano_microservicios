package com.medizano.catalogo.repository;

import com.medizano.catalogo.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByCodigo(String codigo);
    List<Producto> findByEstadoTrue();
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
}

