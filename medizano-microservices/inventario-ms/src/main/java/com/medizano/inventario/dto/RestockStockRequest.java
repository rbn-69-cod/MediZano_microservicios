package com.medizano.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockStockRequest {
    @NotBlank
    private String returnNumber;

    @NotEmpty
    @Valid
    private List<ItemRestock> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemRestock {
        @NotNull private Long productoId;
        @NotNull private Long batchId;
        @NotNull @Min(1) private Integer cantidad;
    }
}
