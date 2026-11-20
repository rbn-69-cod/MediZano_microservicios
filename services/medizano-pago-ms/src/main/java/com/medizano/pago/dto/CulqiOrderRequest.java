package com.medizano.pago.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CulqiOrderRequest {
    @NotNull private Long ordenId;
}
