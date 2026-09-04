package com.medizano.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Schema(description = "Datos completos de un producto del catálogo")
public class ProductoDTO {

    @Schema(description = "ID único del producto", example = "1")
    private Long id;

    @Schema(description = "Nombre comercial del producto", example = "Ibuprofeno 400mg")
    private String nombre;

    @Schema(description = "Código de barras único", example = "7759876543210")
    private String codigo;

    @Schema(description = "Descripción o presentación", example = "Caja con 20 tabletas recubiertas")
    private String descripcion;

    @Schema(description = "Precio de venta al público", example = "6.50")
    private BigDecimal precioVenta;

    @Schema(description = "Precio de compra al proveedor", example = "3.20")
    private BigDecimal precioCompra;

    @Schema(description = "Estado de disponibilidad (true = activo)", example = "true")
    private Boolean estado;

    @Schema(description = "ID de categoría", example = "1")
    private Long categoriaId;

    @Schema(description = "Fecha de registro", example = "2026-09-01T08:00:00")
    private LocalDateTime createdAt;

    public ProductoDTO(Long id, String nombre, String codigo, String descripcion,
                       BigDecimal precioVenta, BigDecimal precioCompra, Boolean estado,
                       Long categoriaId, LocalDateTime createdAt) {
        this.id = id;
        this.nombre = nombre;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.precioVenta = precioVenta;
        this.precioCompra = precioCompra;
        this.estado = estado;
        this.categoriaId = categoriaId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
