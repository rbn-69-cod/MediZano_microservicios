package com.medizano.pago.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CulqiConfigResponse {
    private String publicKey;
    private String currency;
}
