package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillId(Long billId);
    Optional<Payment> findByPaymentReference(String paymentReference);
}

