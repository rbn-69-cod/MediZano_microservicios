package com.medizano.inventario.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddBarcodesRequest {
    @NotEmpty(message = "Debe proporcionar al menos un código de barras")
    private List<String> barcodes;
}

