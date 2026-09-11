package com.medizano.pago.service;

import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderConfirmationService {

    private final PagoRepository pagoRepository;
    private final OrdenClient ordenClient;

    @Value("${security.internal-service-token}")
    private String internalServiceToken;

    @Transactional
    public boolean confirmOrder(Pago pago, String paymentReference) {
        if (Boolean.TRUE.equals(pago.getOrderConfirmed())) {
            return true;
        }

        pago.setConfirmationAttempts((pago.getConfirmationAttempts() == null ? 0 : pago.getConfirmationAttempts()) + 1);
        try {
            ordenClient.confirmarPagoOrden(pago.getOrdenId(), paymentReference, internalServiceToken);
            pago.setOrderConfirmed(true);
            pago.setOrderConfirmedAt(LocalDateTime.now());
            pago.setLastConfirmationError(null);
            pagoRepository.save(pago);
            log.info("Pago {} entregado correctamente a orden-ms para la orden {}", pago.getId(), pago.getOrdenId());
            return true;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            pago.setOrderConfirmed(false);
            pago.setLastConfirmationError(message.substring(0, Math.min(message.length(), 500)));
            pagoRepository.save(pago);
            log.error("Pago {} aprobado, pendiente de confirmar en orden-ms (intento {}): {}",
                    pago.getId(), pago.getConfirmationAttempts(), message);
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${payments.confirmation-retry-delay-ms:30000}")
    public void retryPendingConfirmations() {
        List<Pago> pending = pagoRepository.findApprovedPendingOrderConfirmation(Pago.EstadoPago.APPROVED);
        pending.stream().limit(50).forEach(pago ->
                confirmOrder(pago, buildReference(pago)));
    }

    public String buildReference(Pago pago) {
        if ("MERCADO_PAGO".equalsIgnoreCase(pago.getProvider())) {
            return "MP-" + pago.getMpPaymentId();
        }
        String reference = pago.getPaypalCaptureId();
        if (reference == null || reference.isBlank()) {
            reference = pago.getPaypalOrderId();
        }
        return "PAYPAL-" + reference;
    }
}
