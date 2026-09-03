package com.medizano.inventario.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescuentoStockRequest {
    private String numeroVenta;

    @NotEmpty(message = "Debe enviar al menos un item para descontar")
    private List<ItemDescuento> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemDescuento {
        private Long productoId;
        private Integer cantidad;
    }
}

