package com.medizano.orden.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "inventario-ms")
public interface InventarioClient {

    @PostMapping("/api/v1/inventario/descontar-venta")
    void descontarStockVenta(@RequestBody DescuentoStockRequest request);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class DescuentoStockRequest {
        private String numeroVenta;
        private List<ItemDescuento> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class ItemDescuento {
        private Long productoId;
        private Integer cantidad;
    }
}

