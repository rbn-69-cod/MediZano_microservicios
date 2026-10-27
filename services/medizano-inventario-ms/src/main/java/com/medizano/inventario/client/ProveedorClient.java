package com.medizano.inventario.client;

import com.medizano.inventario.client.dto.ProveedorDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

@FeignClient(name = "proveedor-ms", url = "${app.feign.proveedor-url:}")
public interface ProveedorClient {

    @PostMapping("/api/proveedores/by-ids")
    Map<Long, ProveedorDTO> obtenerProveedoresPorIds(@RequestBody Set<Long> ids);
}

