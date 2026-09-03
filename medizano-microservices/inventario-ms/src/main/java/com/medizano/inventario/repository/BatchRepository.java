package com.medizano.inventario.repository;

import com.medizano.inventario.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByMedicineId(Long medicineId);
    
    @Query("SELECT b FROM Batch b WHERE b.expiryDate < :today ORDER BY b.expiryDate ASC")
    List<Batch> findExpiredBatches(@Param("today") LocalDate today);
    
    @Query("SELECT b FROM Batch b WHERE b.quantityAvailable <= :threshold AND b.expiryDate >= :today ORDER BY b.quantityAvailable ASC")
    List<Batch> findLowStockBatches(@Param("threshold") Integer threshold, @Param("today") LocalDate today);
    
    @Query("SELECT b FROM Batch b ORDER BY b.createdAt DESC")
    List<Batch> findAllOrderByCreatedAtDesc();
    
    Optional<Batch> findByBatchNumber(String batchNumber);
}

