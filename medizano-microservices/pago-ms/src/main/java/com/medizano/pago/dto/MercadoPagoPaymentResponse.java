package com.medizano.pago.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoPaymentResponse {
    private Long pagoId;
    private Long ordenId;
    private String numeroOrden;
    private String paymentId;
    private String preferenceId;
    private String status; // approved, rejected, in_process, pending
    private String statusDetail;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
}

