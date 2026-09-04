package com.medizano.facturacion.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "catalogo-ms")
public interface CatalogoClient {

    @GetMapping("/api/pharmacist/medicines/{id}")
    MedicineClientResponse getMedicineById(@PathVariable("id") Long id);

    @GetMapping("/api/pharmacist/medicines/barcode/{barcode}")
    MedicineClientResponse getMedicineByBarcode(@PathVariable("barcode") String barcode);

    @GetMapping("/api/v1/productos/{id}")
    ProductoClientResponse getProductoById(@PathVariable("id") Long id);

    @Data
    class MedicineClientResponse {
        private Long id;
        private String name;
        private String manufacturer;
        private String category;
        private String barcode;
        private String hsnCode;
        private BigDecimal gstPercentage;
        private BigDecimal sellingPrice;
        private BigDecimal purchasePrice;
    }

    @Data
    class ProductoClientResponse {
        private Long id;
        private String nombre;
        private String codigo;
        private BigDecimal precioVenta;
        private BigDecimal precioCompra;
        private Boolean estado;
    }
}

