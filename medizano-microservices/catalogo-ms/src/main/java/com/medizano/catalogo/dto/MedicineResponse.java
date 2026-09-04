package com.medizano.catalogo.dto;

import com.medizano.catalogo.entity.Medicine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Respuesta con los datos detallados de un medicamento y su estado de stock")
public class MedicineResponse {

    @Schema(description = "Identificador único del medicamento", example = "1")
    private Long id;

    @Schema(description = "Nombre comercial o genérico", example = "Paracetamol 500mg")
    private String name;

    @Schema(description = "Laboratorio fabricante", example = "Laboratorios Bagó")
    private String manufacturer;

    @Schema(description = "Categoría terapéutica", example = "Analgésicos y Antipiréticos")
    private String category;

    @Schema(description = "Código de barras universal (EAN-13)", example = "7751234567890")
    private String barcode;

    @Schema(description = "Código HSN tributario", example = "300490")
    private String hsnCode;

    @Schema(description = "Porcentaje de impuesto IGV aplicado", example = "18.00")
    private BigDecimal gstPercentage;

    @Schema(description = "Requiere receta médica para dispensación", example = "false")
    private Boolean prescriptionRequired;

    @Schema(description = "Estado operativo del medicamento", example = "ACTIVE")
    private Medicine.Status status;

    @Schema(description = "Stock físico total en todos los lotes", example = "150")
    private Integer totalStock;

    @Schema(description = "Stock disponible para venta (lotes no vencidos)", example = "145")
    private Integer availableStock;

    @Schema(description = "Indica si el stock actual está por debajo del umbral mínimo", example = "false")
    private Boolean lowStock;

    @Schema(description = "Indica si el medicamento se encuentra completamente agotado", example = "false")
    private Boolean outOfStock;

    @Schema(description = "Umbral configurado para alerta de stock bajo", example = "10")
    private Integer lowStockThreshold;

    @Schema(description = "Precio de costo / compra unitario al proveedor", example = "2.50")
    private BigDecimal purchasePrice;

    @Schema(description = "Precio de venta al público (PVP) oficial vigente", example = "5.00")
    private BigDecimal sellingPrice;

    @Schema(description = "Fecha y hora de registro en el sistema", example = "2026-09-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora de la última actualización", example = "2026-09-03T18:30:00")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public BigDecimal getGstPercentage() { return gstPercentage; }
    public void setGstPercentage(BigDecimal gstPercentage) { this.gstPercentage = gstPercentage; }
    public Boolean getPrescriptionRequired() { return prescriptionRequired; }
    public void setPrescriptionRequired(Boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
    public Medicine.Status getStatus() { return status; }
    public void setStatus(Medicine.Status status) { this.status = status; }
    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
    public Boolean getLowStock() { return lowStock; }
    public void setLowStock(Boolean lowStock) { this.lowStock = lowStock; }
    public Boolean getOutOfStock() { return outOfStock; }
    public void setOutOfStock(Boolean outOfStock) { this.outOfStock = outOfStock; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
}

