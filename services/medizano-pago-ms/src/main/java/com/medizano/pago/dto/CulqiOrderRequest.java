package com.medizano.pago.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CulqiOrderRequest {
    @NotNull(message = "El ID de la orden es obligatorio")
    private Long ordenId;
}
