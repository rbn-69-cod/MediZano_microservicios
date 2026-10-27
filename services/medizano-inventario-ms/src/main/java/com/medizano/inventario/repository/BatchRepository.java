package com.medizano.inventario.repository;

import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByMedicineId(Long medicineId);

    List<Batch> findByMedicineIdAndExpiryDateGreaterThanEqualOrderByExpiryDateAsc(Long medicineId, LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.id = :id")
    Optional<Batch> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.medicineId = :medicineId ORDER BY b.expiryDate ASC")
    List<Batch> findByMedicineIdForUpdate(@Param("medicineId") Long medicineId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.medicineId = :medicineId AND b.expiryDate >= :today ORDER BY b.expiryDate ASC")
    List<Batch> findSellableByMedicineIdForUpdate(@Param("medicineId") Long medicineId, @Param("today") LocalDate today);

    /**
     * Consulta con bloqueo pesimista para despacho FEFO en ventas y POS.
     * Solo selecciona lotes con estado DISPONIBLE o ALERTA_CANJE, que tengan más de 25 días
     * de vigencia restante (expiryDate > minSafeDate) y stock disponible > 0, ordenados por vencimiento ASC.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.medicineId = :medicineId " +
           "AND b.estado IN (com.medizano.inventario.entity.EstadoLote.DISPONIBLE, com.medizano.inventario.entity.EstadoLote.ALERTA_CANJE) " +
           "AND b.expiryDate > :minSafeDate " +
           "AND b.quantityAvailable > 0 " +
           "ORDER BY b.expiryDate ASC")
    List<Batch> findFefoEligibleBatchesForUpdate(
            @Param("medicineId") Long medicineId,
            @Param("minSafeDate") LocalDate minSafeDate
    );

    /**
     * Consulta lotes activos para la tarea programada diaria de evaluación de vencimientos.
     * Cantidad disponible > 0 y estado NOT IN ('VENCIDO_BAJA', 'DEVUELTO').
     */
    @Query("SELECT b FROM Batch b WHERE b.quantityAvailable > 0 " +
           "AND b.estado NOT IN (com.medizano.inventario.entity.EstadoLote.VENCIDO_BAJA, com.medizano.inventario.entity.EstadoLote.DEVUELTO) " +
           "ORDER BY b.expiryDate ASC")
    List<Batch> findBatchesForEvaluation();

    @Query("SELECT b FROM Batch b WHERE b.expiryDate < :today ORDER BY b.expiryDate ASC")
    List<Batch> findExpiredBatches(@Param("today") LocalDate today);

    @Query("SELECT b FROM Batch b WHERE b.quantityAvailable <= :threshold AND b.expiryDate >= :today ORDER BY b.quantityAvailable ASC")
    List<Batch> findLowStockBatches(@Param("threshold") Integer threshold, @Param("today") LocalDate today);

    @Query("SELECT b FROM Batch b ORDER BY b.createdAt DESC")
    List<Batch> findAllOrderByCreatedAtDesc();

    List<Batch> findByEstado(EstadoLote estado);

    Optional<Batch> findByBatchNumber(String batchNumber);

    Optional<Batch> findByMedicineIdAndBatchNumber(Long medicineId, String batchNumber);
}
