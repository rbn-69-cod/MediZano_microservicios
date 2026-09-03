package com.medizano.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReturnRequest {
    @NotNull(message = "El ID de la venta es obligatorio")
    private Long billId;
    
    @NotBlank(message = "El motivo es obligatorio")
    private String reason;
    
    @NotEmpty(message = "Debe incluir al menos un producto a devolver")
    @Valid
    private List<ReturnItemRequest> items;
}

