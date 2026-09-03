package com.medizano.orden.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearOrdenRequest {

    private Long clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private Long usuarioId;
    private String usuarioNombre;

    @NotNull(message = "El método de pago es obligatorio (EFECTIVO o PAYPAL)")
    private String metodoPago;

    @NotEmpty(message = "Debe agregar al menos un producto a la orden")
    private List<ItemOrdenRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemOrdenRequest {
        @NotNull(message = "El ID del producto es obligatorio")
        private Long productoId;

        @NotNull(message = "La cantidad es obligatoria")
        private Integer cantidad;
    }
}

