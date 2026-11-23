package com.medizano.pago.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CulqiOrderResponse {
    private Long pagoId;
    private Long ordenId;
    private String numeroOrden;
    private String culqiOrderId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime expiresAt;
}
