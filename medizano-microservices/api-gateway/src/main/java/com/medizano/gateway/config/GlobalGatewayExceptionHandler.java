package com.medizano.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GlobalGatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        log.error("Excepción interceptada en API Gateway para la ruta '{}': {}", path, ex.getMessage());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "Error interno en la pasarela de servicios";
        String service = extractServiceFromPath(path);

        String exName = ex.getClass().getName();
        String exMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        // Detección de caída o desconexión del microservicio downstream
        if (exMsg.contains("connection refused") ||
            exMsg.contains("failed to resolve") ||
            exMsg.contains("unable to find instance") ||
            exMsg.contains("finishconnect") ||
            exMsg.contains("connection reset") ||
            exName.contains("ConnectException") ||
            exName.contains("NotFoundException") ||
            exName.contains("TimeoutException") ||
            (ex instanceof ResponseStatusException && ((ResponseStatusException) ex).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE)) {

            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorMessage = "El microservicio solicitado se encuentra temporalmente no disponible";
        } else if (ex instanceof ResponseStatusException) {
            status = HttpStatus.resolve(((ResponseStatusException) ex).getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = ((ResponseStatusException) ex).getReason() != null ?
                    ((ResponseStatusException) ex).getReason() : status.getReasonPhrase();
        }

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("service", service);
        errorAttributes.put("path", path);
        errorAttributes.put("message", errorMessage);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(errorAttributes).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Service Unavailable\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String extractServiceFromPath(String path) {
        if (path.startsWith("/api/pharmacist/medicines") || path.startsWith("/api/v1/productos")) return "catalogo-ms";
        if (path.startsWith("/api/pharmacist/batches") || path.startsWith("/api/v1/inventario")) return "inventario-ms";
        if (path.startsWith("/api/cashier/bills") || path.startsWith("/api/admin/reports") || path.startsWith("/api/cashier/returns")) return "facturacion-ms";
        if (path.startsWith("/api/v1/pagos") || path.startsWith("/api/cashier/paypal") || path.startsWith("/api/cashier/mercadopago")) return "pago-ms";
        if (path.startsWith("/api/auth") || path.startsWith("/api/admin/users") || path.startsWith("/api/admin/audit")) return "usuario-ms";
        if (path.startsWith("/api/v1/clientes")) return "cliente-ms";
        if (path.startsWith("/api/v1/ordenes")) return "orden-ms";
        return "api-gateway";
    }
}

