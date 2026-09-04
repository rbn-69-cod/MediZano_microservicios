package com.medizano.catalogo.controller;

import com.medizano.catalogo.dto.ProductoDTO;
import com.medizano.catalogo.dto.ProductoRequest;
import com.medizano.catalogo.service.ProductoService;
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
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Catálogo v1", description = "API v1 de productos y medicamentos (compatibilidad con frontend legado y POS)")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar productos activos", description = "Devuelve el catálogo de productos. Permite filtrar solo los activos o consultar la lista completa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de productos recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<ProductoDTO>> listar(
            @Parameter(description = "Si es true incluye inactivos; si es false devuelve solo activos", example = "false")
            @RequestParam(value = "todos", defaultValue = "false") boolean todos) {
        if (todos) {
            return ResponseEntity.ok(productoService.listarTodos());
        }
        return ResponseEntity.ok(productoService.listarActivos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID", description = "Obtiene los detalles comerciales, código y precio de un producto según su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado", content = @Content(schema = @Schema(implementation = ProductoDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado con el ID especificado")
    })
    public ResponseEntity<ProductoDTO> obtenerPorId(
            @Parameter(description = "ID único del producto", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productoService.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Obtener producto por código de barras", description = "Localiza de forma unívoca un producto utilizando su código de barras registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado", content = @Content(schema = @Schema(implementation = ProductoDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "No existe un producto con el código de barras proporcionado")
    })
    public ResponseEntity<ProductoDTO> obtenerPorCodigo(
            @Parameter(description = "Código de barras del producto", example = "7751234567890", required = true)
            @PathVariable String codigo) {
        return ResponseEntity.ok(productoService.buscarPorCodigo(codigo));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo producto", description = "Registra un producto en el catálogo general validando unicidad de código de barras y precios positivos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado exitosamente", content = @Content(schema = @Schema(implementation = ProductoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (ej. precio <= 0 o nombre vacío)"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere permisos de farmacéutico o administrador")
    })
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoRequest request) {
        ProductoDTO producto = productoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(producto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar producto", description = "Modifica los datos comerciales, descripción o precios de compra y venta del producto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado con éxito", content = @Content(schema = @Schema(implementation = ProductoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Valores de actualización no válidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<ProductoDTO> actualizar(
            @Parameter(description = "ID del producto a actualizar", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar producto", description = "Cambia el estado lógico del producto para habilitar o deshabilitar su venta.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estado modificado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<Void> cambiarEstado(
            @Parameter(description = "ID del producto", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "true para activar, false para desactivar", example = "true", required = true)
            @RequestParam boolean activo) {
        productoService.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar producto", description = "Elimina lógicamente un producto del catálogo si no posee dependencias activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado")
    })
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del producto a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

