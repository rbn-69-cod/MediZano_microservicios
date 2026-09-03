package com.medizano.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:MedicalStorePOSSecretKeyForJWTTokenGeneration2024Production}")
    private String jwtSecret;

    private static final List<String> PUBLIC_URLS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/v1/pagos/config",
            "/api/v1/pagos/mercadopago/webhook",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Permitir OPTIONS para CORS pre-flight
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Permitir endpoints públicos
        if (isPublicUrl(path)) {
            return chain.filter(exchange);
        }

        // Obtener header Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Petición sin token JWT a ruta protegida: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token JWT no proporcionado");
        }

        String token = authHeader.substring(7).trim();

        // Validar token y extraer claims
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token expirado para ruta: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token JWT expirado");
        } catch (SignatureException | MalformedJwtException e) {
            log.warn("Token inválido o manipulado para ruta: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token JWT inválido o manipulado");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Error validando token JWT para ruta {}: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token JWT inválido");
        }

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        if (role == null) {
            role = "ROLE_USER";
        }
        String normalizedRole = role.replace("ROLE_", "").toUpperCase();

        log.debug("Petición autenticada: usuario={}, rol={}, path={}", username, normalizedRole, path);

        // Control de Acceso Basado en Roles (RBAC)
        if (path.startsWith("/api/admin/")) {
            if (!normalizedRole.equals("ADMIN")) {
                log.warn("Acceso denegado a admin: usuario={}, rol={}", username, normalizedRole);
                return onError(exchange, HttpStatus.FORBIDDEN, "Acceso denegado: Se requiere rol de Administrador");
            }
        } else if (path.startsWith("/api/pharmacist/")) {
            if (!normalizedRole.equals("ADMIN") && !normalizedRole.equals("PHARMACIST") && !normalizedRole.equals("STOCK_MONITOR")) {
                log.warn("Acceso denegado a farmacia: usuario={}, rol={}", username, normalizedRole);
                return onError(exchange, HttpStatus.FORBIDDEN, "Acceso denegado: Se requiere rol de Farmacéutico o Administrador");
            }
        } else if (path.startsWith("/api/cashier/")) {
            if (!normalizedRole.equals("ADMIN") && !normalizedRole.equals("CASHIER")) {
                log.warn("Acceso denegado a caja: usuario={}, rol={}", username, normalizedRole);
                return onError(exchange, HttpStatus.FORBIDDEN, "Acceso denegado: Se requiere rol de Cajero o Administrador");
            }
        }

        // Reenviar identidad en headers internos
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Auth-User", username)
                .header("X-Auth-Role", normalizedRole)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicUrl(String path) {
        return PUBLIC_URLS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getURI().getPath();
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Alta prioridad antes de los filtros de ruteo
    }
}

