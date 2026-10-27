package com.medizano.proveedor.controller;

import com.medizano.proveedor.dto.ProveedorDTO;
import com.medizano.proveedor.entity.Proveedor;
import com.medizano.proveedor.service.ProveedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping({"/api/v1/proveedores", "/api/proveedores", "/v1/proveedores"})
@RequiredArgsConstructor
@Tag(name = "Proveedores", description = "Gestión de laboratorios y proveedores farmacéuticos (No Transaccional)")
public class ProveedorController {

    private final ProveedorService proveedorService;

    @PostMapping("/by-ids")
    @Operation(summary = "Obtener proveedores por IDs en lote (evita N+1)")
    public ResponseEntity<Map<Long, ProveedorDTO>> obtenerProveedoresPorIds(@RequestBody Set<Long> ids) {
        return ResponseEntity.ok(proveedorService.buscarPorIds(ids));
    }

    @GetMapping("/paginado")
    @Operation(summary = "Listar proveedores con paginación y búsqueda")
    public ResponseEntity<Page<Proveedor>> listarPaginado(
            @RequestParam(required = false, name = "search") String search,
            @RequestParam(required = false, name = "filtro") String filtro,
            @RequestParam(required = false, defaultValue = "false") boolean soloActivos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String term = (search != null && !search.isBlank()) ? search : filtro;

        Sort sortObj = Sort.by(Sort.Direction.DESC, "id");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String prop = parts[0].trim();
            Sort.Direction dir = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            try {
                sortObj = Sort.by(dir, prop);
            } catch (Exception ignored) {
                sortObj = Sort.by(Sort.Direction.DESC, "id");
            }
        }

        Pageable pageable = PageRequest.of(page, size, sortObj);
        return ResponseEntity.ok(proveedorService.buscar(term, soloActivos, pageable));
    }

    @GetMapping
    @Operation(summary = "Listar todos los proveedores registrados")
    public ResponseEntity<List<Proveedor>> listarTodos(
            @RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        if (soloActivos) {
            return ResponseEntity.ok(proveedorService.listarActivos());
        }
        return ResponseEntity.ok(proveedorService.listarTodos());
    }

    @GetMapping("/{id:[0-9]+}")
    @Operation(summary = "Obtener proveedor por ID")
    public ResponseEntity<Proveedor> obtenerPorId(@PathVariable Long id) {
        return proveedorService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({"/ruc/{ruc}", "/por-ruc/{ruc}"})
    @Operation(summary = "Buscar proveedor por RUC")
    public ResponseEntity<Proveedor> obtenerPorRuc(@PathVariable String ruc) {
        return proveedorService.buscarPorRuc(ruc)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Registrar un nuevo laboratorio o proveedor")
    public ResponseEntity<Proveedor> crear(@Valid @RequestBody Proveedor proveedor) {
        Proveedor creado = proveedorService.crear(proveedor);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id:[0-9]+}")
    @Operation(summary = "Actualizar datos del proveedor")
    public ResponseEntity<Proveedor> actualizar(@PathVariable Long id, @Valid @RequestBody Proveedor proveedor) {
        return ResponseEntity.ok(proveedorService.actualizar(id, proveedor));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @Operation(summary = "Desactivar o eliminar proveedor (según reglas de integridad de compras)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        proveedorService.eliminarOdesactivar(id);
        return ResponseEntity.noContent().build();
    }
}
