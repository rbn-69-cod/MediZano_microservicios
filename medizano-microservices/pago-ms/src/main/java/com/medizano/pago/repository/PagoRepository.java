package com.medizano.pago.repository;

import com.medizano.pago.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByOrdenIdOrderByCreatedAtDesc(Long ordenId);
    Optional<Pago> findByPaypalOrderId(String paypalOrderId);
    Optional<Pago> findByMpPreferenceId(String mpPreferenceId);
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
    Optional<Pago> findByOrdenIdAndStatus(Long ordenId, Pago.EstadoPago status);
}
