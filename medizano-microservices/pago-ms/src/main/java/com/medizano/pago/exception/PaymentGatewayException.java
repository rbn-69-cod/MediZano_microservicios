package com.medizano.pago.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentGatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String gateway;
    private final boolean configured;

    public PaymentGatewayException(String message, HttpStatus status, String gateway, boolean configured) {
        super(message);
        this.status = status;
        this.gateway = gateway;
        this.configured = configured;
    }
}
