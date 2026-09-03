package com.medizano.orden.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cliente-ms")
public interface ClienteClient {

    @GetMapping("/api/v1/clientes/{id}")
    ClienteResponse obtenerClientePorId(@PathVariable("id") Long id);

    @Data
    class ClienteResponse {
        private Long id;
        private String nombre;
        private String documento;
        private String email;
        private String telefono;
    }
}

