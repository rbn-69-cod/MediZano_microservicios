package com.medizano.facturacion.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "inventario-ms")
public interface InventarioClient {

    @GetMapping("/api/pharmacist/batches")
    List<BatchClientResponse> getAllBatches();

    @GetMapping("/api/pharmacist/batches/medicine/{medicineId}")
    List<BatchClientResponse> getBatchesByMedicine(@PathVariable("medicineId") Long medicineId);

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/inventario/descontar-venta")
    void descontarStockVenta(@org.springframework.web.bind.annotation.RequestBody DescuentoStockRequest request);

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class DescuentoStockRequest {
        private String numeroVenta;
        private List<ItemDescuento> items;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    class ItemDescuento {
        private Long productoId;
        private Integer cantidad;
    }

    @Data
    class BatchClientResponse {
        private Long id;
        private Long medicineId;
        private String medicineName;
        private String batchNumber;
        private LocalDate expiryDate;
        private BigDecimal purchasePrice;
        private BigDecimal sellingPrice;
        private Integer quantityAvailable;
        private Boolean expired;
    }
}
