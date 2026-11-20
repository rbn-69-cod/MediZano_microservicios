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

    /** References returned by Culqi.  Existing provider columns are intentionally
     * left in the database by Hibernate; historical transactions are not deleted. */
    @Column(length = 100, unique = true)
    private String culqiOrderId;

    @Column(length = 100, unique = true)
    private String culqiChargeId;

    @Column(length = 100)
    private String idempotencyKey;

    private LocalDateTime expiresAt;

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
    private String provider = "CULQI"; // CULQI, CASH

    private LocalDateTime paymentDate;

    @Column(length = 50)
    private String externalStatus; // COMPLETED, approved, rejected, in_process

    @Builder.Default
    @Column(nullable = false)
    private Boolean orderConfirmed = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer confirmationAttempts = 0;

    @Column(length = 500)
    private String lastConfirmationError;

    private LocalDateTime orderConfirmedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = EstadoPago.PENDING;
        }
        if (provider == null) {
            provider = "CULQI";
        }
        if (orderConfirmed == null) {
            orderConfirmed = false;
        }
        if (confirmationAttempts == null) {
            confirmationAttempts = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EstadoPago {
        PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED, REVIEW_REQUIRED
    }
}
