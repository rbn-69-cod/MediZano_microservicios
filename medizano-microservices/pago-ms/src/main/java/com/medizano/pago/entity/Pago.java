package com.medizano.pago.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El ID de la orden es obligatorio")
    @Column(nullable = false)
    private Long ordenId;

    @Column(length = 60)
    private String numeroOrden;

    // PayPal reference fields
    @Column(length = 100)
    private String paypalOrderId;

    @Column(length = 100)
    private String paypalCaptureId;

    // Mercado Pago reference fields
    @Column(length = 100)
    private String mpPreferenceId;

    @Column(length = 100)
    private String mpPaymentId;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency; // USD, PEN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPago status;

    @Builder.Default
    @Column(length = 30)
    private String provider = "PAYPAL"; // PAYPAL, MERCADO_PAGO, CASH

    private LocalDateTime paymentDate;

    @Column(length = 50)
    private String externalStatus; // COMPLETED, approved, rejected, in_process

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = EstadoPago.PENDING;
        }
        if (provider == null) {
            provider = "PAYPAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EstadoPago {
        PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED
    }
}
