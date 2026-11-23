package com.medizano.orden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidarRecojoRequest {
    @NotBlank(message = "El código de recojo es obligatorio")
    private String codigoRecojo;
}
