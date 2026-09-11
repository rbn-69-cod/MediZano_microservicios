package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.Return;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, Long> {
    Optional<Return> findByReturnNumber(String returnNumber);
    List<Return> findByOriginalBillId(Long billId);

    @Query("SELECT r FROM Return r WHERE r.inventoryRestored = false ORDER BY r.createdAt ASC")
    List<Return> findPendingInventoryRestoration();
}
