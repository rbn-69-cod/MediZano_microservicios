package com.medizano.pago.exception;

import org.springframework.http.HttpStatus;

public class PaymentGatewayNotConfiguredException extends PaymentGatewayException {
    public PaymentGatewayNotConfiguredException(String gateway, String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, gateway, false);
    }
}
