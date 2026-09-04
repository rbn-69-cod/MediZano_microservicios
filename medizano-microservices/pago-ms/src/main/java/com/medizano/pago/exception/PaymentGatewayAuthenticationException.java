package com.medizano.pago.exception;

import org.springframework.http.HttpStatus;

public class PaymentGatewayAuthenticationException extends PaymentGatewayException {
    public PaymentGatewayAuthenticationException(String gateway, String message) {
        super(message, HttpStatus.FAILED_DEPENDENCY, gateway, false);
    }
}
