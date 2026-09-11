package com.medizano.pago.repository;

import com.medizano.pago.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByOrdenIdOrderByCreatedAtDesc(Long ordenId);
    Optional<Pago> findByPaypalOrderId(String paypalOrderId);
    Optional<Pago> findByMpPreferenceId(String mpPreferenceId);
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
    Optional<Pago> findByOrdenIdAndStatus(Long ordenId, Pago.EstadoPago status);
    Optional<Pago> findFirstByOrdenIdAndProviderAndStatusOrderByCreatedAtDesc(Long ordenId, String provider, Pago.EstadoPago status);

    @Query("SELECT p FROM Pago p WHERE p.status = :status " +
            "AND (p.orderConfirmed = false OR p.orderConfirmed IS NULL) ORDER BY p.updatedAt ASC")
    List<Pago> findApprovedPendingOrderConfirmation(@Param("status") Pago.EstadoPago status);
}
