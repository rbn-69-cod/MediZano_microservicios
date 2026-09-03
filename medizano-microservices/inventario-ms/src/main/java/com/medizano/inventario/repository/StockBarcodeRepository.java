package com.medizano.inventario.repository;

import com.medizano.inventario.entity.StockBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockBarcodeRepository extends JpaRepository<StockBarcode, Long> {
    Optional<StockBarcode> findByBarcode(String barcode);
    List<StockBarcode> findByBatchId(Long batchId);
    List<StockBarcode> findByBatchIdAndSoldFalse(Long batchId);
    boolean existsByBarcode(String barcode);
}

