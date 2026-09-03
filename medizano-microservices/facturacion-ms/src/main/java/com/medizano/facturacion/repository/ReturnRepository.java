package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.Return;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, Long> {
    Optional<Return> findByReturnNumber(String returnNumber);
    List<Return> findByOriginalBillId(Long billId);
}

