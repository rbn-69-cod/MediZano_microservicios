package com.medizano.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBillRequest {
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private BigDecimal cashTendered;

    @NotEmpty(message = "Debe incluir al menos un producto en la venta")
    @Valid
    private List<BillItemRequest> items;

    private List<PaymentRequest> payments;
}

