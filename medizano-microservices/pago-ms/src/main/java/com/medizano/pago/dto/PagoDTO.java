package com.medizano.pago.dto;

import com.medizano.pago.entity.Pago;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoDTO {
    private Long id;
    private Long ordenId;
    private String numeroOrden;
    private String paypalOrderId;
    private String paypalCaptureId;
    private BigDecimal amount;
    private String currency;
    private Pago.EstadoPago status;
    private String provider;
    private String externalStatus;
    private LocalDateTime paymentDate;
    private LocalDateTime createdAt;
}

