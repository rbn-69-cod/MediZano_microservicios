package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {
    Optional<Factura> findByNumeroFactura(String numeroFactura);
    Optional<Factura> findByOrdenId(Long ordenId);
    List<Factura> findTop50ByOrderByCreatedAtDesc();
}

