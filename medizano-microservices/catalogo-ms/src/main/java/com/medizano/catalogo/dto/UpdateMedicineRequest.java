package com.medizano.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMedicineRequest {
    @NotBlank(message = "Medicine name is required")
    private String name;

    @NotBlank(message = "Manufacturer is required")
    private String manufacturer;

    private String category;
    private String barcode;

    @NotBlank(message = "HSN code is required")
    private String hsnCode;

    @NotNull(message = "GST percentage is required")
    @PositiveOrZero(message = "GST percentage must be positive or zero")
    private BigDecimal gstPercentage;

    private Boolean prescriptionRequired;

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
}

