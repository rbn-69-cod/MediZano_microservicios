package com.medizano.orden.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "catalogo-ms")
public interface CatalogoClient {

    @GetMapping("/api/v1/productos/{id}")
    ProductoResponse obtenerProductoPorId(@PathVariable("id") Long id);

    @Data
    class ProductoResponse {
        private Long id;
        private String nombre;
        private String codigo;
        private BigDecimal precioVenta;
        private Boolean estado;
    }
}

