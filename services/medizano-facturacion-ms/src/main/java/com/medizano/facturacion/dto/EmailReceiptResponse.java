package com.medizano.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailReceiptResponse {
    private boolean success;
    private String recipient;
    private String seriesNumber;
    private String message;
    private String timestamp;
}

