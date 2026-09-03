package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.ItemFactura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemFacturaRepository extends JpaRepository<ItemFactura, Long> {
    List<ItemFactura> findByFacturaId(Long facturaId);
}

