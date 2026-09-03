package com.medizano.facturacion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerarFacturaRequest {
    @NotNull
    private Long ordenId;
    private String numeroOrden;
    private Long clienteId;
    private String clienteNombre;
    private String metodoPago;
    private String referenciaPago;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private List<ItemFacturaRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemFacturaRequest {
        private Long productoId;
        private String productoNombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}

