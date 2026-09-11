package com.medizano.inventario.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescuentoStockRequest {
    @NotBlank
    private String numeroVenta;

    @NotEmpty(message = "Debe enviar al menos un item para descontar")
    @Valid
    private List<ItemDescuento> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemDescuento {
        private Long batchId;
        @NotNull private Long productoId;
        @NotNull @Min(1) private Integer cantidad;
    }
}
