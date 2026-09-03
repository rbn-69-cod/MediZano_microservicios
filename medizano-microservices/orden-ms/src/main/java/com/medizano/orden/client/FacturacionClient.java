package com.medizano.orden.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "facturacion-ms")
public interface FacturacionClient {

    @PostMapping("/api/v1/facturacion/generar")
    void generarFactura(@RequestBody GenerarFacturaRequest request);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class GenerarFacturaRequest {
        private Long ordenId;
        private String numeroOrden;
        private Long clienteId;
        private String clienteNombre;
        private String metodoPago;
        private String referenciaPago;
        private BigDecimal subtotal;
        private BigDecimal impuesto;
        private BigDecimal total;
        private List<ItemFactura> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class ItemFactura {
        private Long productoId;
        private String productoNombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}

