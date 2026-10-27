package com.medizano.inventario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class LoteNoAptoVentaException extends RuntimeException {

    public LoteNoAptoVentaException(String message) {
        super(message);
    }

    public LoteNoAptoVentaException() {
        super("Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica (<= 25 días) o en cuarentena. Trasladar producto a área de bajas.");
    }
}

