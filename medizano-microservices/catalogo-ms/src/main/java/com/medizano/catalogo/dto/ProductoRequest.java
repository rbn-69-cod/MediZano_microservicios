package com.medizano.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Datos para crear o actualizar un producto del catálogo")
public class ProductoRequest {

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Schema(description = "Nombre comercial del producto", example = "Ibuprofeno 400mg")
    private String nombre;

    @Schema(description = "Código de barras único del producto", example = "7759876543210")
    private String codigo;

    @Schema(description = "Descripción o presentación del producto", example = "Caja con 20 tabletas recubiertas")
    private String descripcion;

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio de venta debe ser mayor a 0")
    @Schema(description = "Precio de venta al público en soles", example = "6.50")
    private BigDecimal precioVenta;

    @DecimalMin(value = "0.0", message = "El precio de compra no puede ser negativo")
    @Schema(description = "Precio de costo o compra al proveedor", example = "3.20")
    private BigDecimal precioCompra;

    @Schema(description = "Estado de activación (true = activo para la venta)", example = "true")
    private Boolean estado;

    @Schema(description = "ID de la categoría a la que pertenece el producto", example = "1")
    private Long categoriaId;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }
    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }
    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
}
