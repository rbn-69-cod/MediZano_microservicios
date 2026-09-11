package com.medizano.inventario.repository;

import com.medizano.inventario.entity.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryOperationRepository extends JpaRepository<InventoryOperation, Long> {
    boolean existsByOperationKey(String operationKey);
}
