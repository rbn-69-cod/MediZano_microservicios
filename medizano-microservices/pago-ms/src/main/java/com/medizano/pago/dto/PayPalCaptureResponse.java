package com.medizano.pago.dto;

import com.medizano.pago.entity.Pago;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayPalCaptureResponse {
    private Long pagoId;
    private Long ordenId;
    private String numeroOrden;
    private String paypalOrderId;
    private String paypalCaptureId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
}

