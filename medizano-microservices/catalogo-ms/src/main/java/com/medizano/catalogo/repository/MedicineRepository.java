package com.medizano.catalogo.repository;

import com.medizano.catalogo.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    List<Medicine> findByNameContainingIgnoreCase(String name);

    Optional<Medicine> findByBarcode(String barcode);

    List<Medicine> findByBarcodeStartingWith(String prefix);

    Optional<Medicine> findByHsnCode(String hsnCode);

    List<Medicine> findByStatus(Medicine.Status status);

    boolean existsByHsnCode(String hsnCode);
}

