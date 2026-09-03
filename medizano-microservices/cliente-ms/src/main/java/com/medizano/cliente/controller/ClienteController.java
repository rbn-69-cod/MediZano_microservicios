package com.medizano.cliente.controller;

import com.medizano.cliente.dto.ClienteDTO;
import com.medizano.cliente.dto.ClienteRequest;
import com.medizano.cliente.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Microservicio de Gestión de Clientes (Kenyi)")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @Operation(summary = "Listar clientes")
    public ResponseEntity<List<ClienteDTO>> listar(@RequestParam(value = "todos", defaultValue = "false") boolean todos) {
        if (todos) {
            return ResponseEntity.ok(clienteService.listarTodos());
        }
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cliente por ID")
    public ResponseEntity<ClienteDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/documento/{documento}")
    @Operation(summary = "Obtener cliente por DNI/RUC")
    public ResponseEntity<ClienteDTO> obtenerPorDocumento(@PathVariable String documento) {
        return ResponseEntity.ok(clienteService.buscarPorDocumento(documento));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo cliente")
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteRequest request) {
        ClienteDTO cliente = clienteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cliente);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cliente")
    public ResponseEntity<ClienteDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar cliente")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

