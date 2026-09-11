package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {
    List<ReturnItem> findByReturnEntityId(Long returnId);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM ReturnItem ri WHERE ri.billItemId = :billItemId")
    Long sumQuantityByBillItemId(@Param("billItemId") Long billItemId);

    @Query("SELECT COUNT(ri) FROM ReturnItem ri WHERE ri.returnEntity.originalBill.id = :billId AND ri.billItemId IS NULL")
    Long countLegacyItemsByBillId(@Param("billId") Long billId);
}
