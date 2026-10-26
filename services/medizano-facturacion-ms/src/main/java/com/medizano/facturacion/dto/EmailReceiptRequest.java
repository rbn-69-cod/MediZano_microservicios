package com.medizano.facturacion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailReceiptRequest {
    @NotBlank(message = "El correo destino es obligatorio")
    @Email(message = "El formato de correo no es válido")
    private String to;

    private String seriesNumber;
    private String customerName;
    private String customerDoc;
    private String cashier;
    private String paymentMode;
    private String date;

    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;

    private List<EmailReceiptItemDTO> items;
}
