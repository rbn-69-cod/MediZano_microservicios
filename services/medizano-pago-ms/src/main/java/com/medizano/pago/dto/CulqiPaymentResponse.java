package com.medizano.pago.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class CulqiPaymentResponse {
    private Long pagoId;
    private Long ordenId;
    private String numeroOrden;
    private String transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
    private Boolean orderConfirmed;
}
