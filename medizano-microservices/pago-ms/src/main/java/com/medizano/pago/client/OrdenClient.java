package com.medizano.pago.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;

@FeignClient(name = "orden-ms")
public interface OrdenClient {

    @GetMapping("/api/v1/ordenes/{id}")
    OrdenResponse obtenerOrdenPorId(@PathVariable("id") Long id);

    @PostMapping("/api/v1/ordenes/{id}/confirmar-pago")
    void confirmarPagoOrden(@PathVariable("id") Long id,
                            @RequestParam("referenciaPago") String referenciaPago,
                            @RequestHeader("X-Internal-Service-Token") String internalServiceToken);

    @Data
    class OrdenResponse {
        private Long id;
        private String numeroOrden;
        private BigDecimal total;
        private String estado;
        private String metodoPago;
        private String clienteNombre;
        private String clienteEmail;
    }
}
