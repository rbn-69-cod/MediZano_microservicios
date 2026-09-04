package com.medizano.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para registrar un nuevo medicamento en el catálogo")
public class CreateMedicineRequest {

    @NotBlank(message = "Medicine name is required")
    @Schema(description = "Nombre comercial o genérico del medicamento", example = "Paracetamol 500mg")
    private String name;

    @NotBlank(message = "Manufacturer is required")
    @Schema(description = "Laboratorio o empresa fabricante", example = "Laboratorios Bagó")
    private String manufacturer;

    @Schema(description = "Categoría terapéutica del medicamento", example = "Analgésicos y Antipiréticos")
    private String category;

    @Schema(description = "Código de barras universal (EAN-13)", example = "7751234567890")
    private String barcode;

    @NotBlank(message = "HSN code is required")
    @Schema(description = "Código del Sistema Armonizado (HSN / Tributario)", example = "300490")
    private String hsnCode;

    @NotNull(message = "GST percentage is required")
    @PositiveOrZero(message = "GST percentage must be positive or zero")
    @Schema(description = "Porcentaje de impuesto IGV / GST aplicable", example = "18.00")
    private BigDecimal gstPercentage;

    @Schema(description = "Indica si requiere receta médica para su expendio", example = "false")
    private Boolean prescriptionRequired;

    @Schema(description = "Cantidad de stock inicial a registrar en el lote inicial", example = "100")
    private Integer initialStock;

    @Schema(description = "Precio de compra unitario al proveedor", example = "2.50")
    private BigDecimal purchasePrice;

    @Schema(description = "Precio de venta sugerido al público", example = "5.00")
    private BigDecimal sellingPrice;

    @Schema(description = "Número o código del lote de fabricación inicial", example = "LOTE-2026-A")
    private String batchNumber;

    @Schema(description = "Fecha de caducidad del lote inicial (YYYY-MM-DD)", example = "2027-12-31")
    private LocalDate expiryDate;

    @Schema(description = "Lista opcional de códigos de barra serializados individuales", example = "[\"7751234567890-001\", \"7751234567890-002\"]")
    private List<String> barcodes;

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
    public Boolean getPrescriptionRequired() { return prescriptionRequired != null && prescriptionRequired; }
    public void setPrescriptionRequired(Boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
    public Integer getInitialStock() { return initialStock; }
    public void setInitialStock(Integer initialStock) { this.initialStock = initialStock; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public List<String> getBarcodes() { return barcodes; }
    public void setBarcodes(List<String> barcodes) { this.barcodes = barcodes; }
}

