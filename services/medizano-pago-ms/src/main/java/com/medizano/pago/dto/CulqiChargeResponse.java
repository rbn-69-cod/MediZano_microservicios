package com.medizano.pago.dto;

import com.medizano.pago.entity.Pago;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CulqiChargeResponse {

    private Long pagoId;
    private Long ordenId;
    private String numeroOrden;
    private String chargeId;
    private BigDecimal amount;
    private String currency;
    private Pago.EstadoPago status;
    private String message;
    private Boolean orderConfirmed;
}
