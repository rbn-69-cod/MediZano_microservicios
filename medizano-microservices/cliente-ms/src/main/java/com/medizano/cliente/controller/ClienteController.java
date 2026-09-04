package com.medizano.cliente.controller;

import com.medizano.cliente.dto.ClienteDTO;
import com.medizano.cliente.dto.ClienteRequest;
import com.medizano.cliente.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Clientes", description = "Gestión integral de clientes, identificación tributaria (DNI) y datos de contacto")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @Operation(summary = "Listar clientes", description = "Obtiene el listado de clientes registrados con opción de filtrar solo los activos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de clientes recuperada"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<ClienteDTO>> listar(
            @Parameter(description = "true para listar todos los clientes, false para activos únicamente", example = "false")
            @RequestParam(value = "todos", defaultValue = "false") boolean todos) {
        if (todos) {
            return ResponseEntity.ok(clienteService.listarTodos());
        }
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cliente por ID", description = "Consulta la información de un cliente específico según su identificador único.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado", content = @Content(schema = @Schema(implementation = ClienteDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    })
    public ResponseEntity<ClienteDTO> obtenerPorId(
            @Parameter(description = "ID del cliente", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/documento/{documento}")
    @Operation(summary = "Obtener cliente por DNI", description = "Busca de forma exacta un cliente por su número de documento de identidad (DNI o Carné de Extranjería).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado", content = @Content(schema = @Schema(implementation = ClienteDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "No existe cliente con el documento provisto")
    })
    public ResponseEntity<ClienteDTO> obtenerPorDocumento(
            @Parameter(description = "Número de documento de identidad", example = "12345678", required = true)
            @PathVariable String documento) {
        return ResponseEntity.ok(clienteService.buscarPorDocumento(documento));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo cliente", description = "Registra un cliente en la base de datos validando la unicidad del documento y formato del correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente creado exitosamente", content = @Content(schema = @Schema(implementation = ClienteDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de cliente inválidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "409", description = "Ya existe un cliente registrado con ese número de documento")
    })
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteRequest request) {
        ClienteDTO cliente = clienteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cliente);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cliente", description = "Actualiza los datos personales, dirección o teléfono de un cliente registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente actualizado exitosamente", content = @Content(schema = @Schema(implementation = ClienteDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de actualización inválidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    })
    public ResponseEntity<ClienteDTO> actualizar(
            @Parameter(description = "ID del cliente a modificar", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar cliente", description = "Elimina o desactiva lógicamente un cliente del sistema si no posee transacciones activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cliente eliminado"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Cliente no encontrado")
    })
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del cliente a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

