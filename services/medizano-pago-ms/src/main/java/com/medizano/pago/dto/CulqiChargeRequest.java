package com.medizano.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CulqiChargeRequest {
    @NotNull private Long ordenId;
    @NotBlank private String tokenId;
}
