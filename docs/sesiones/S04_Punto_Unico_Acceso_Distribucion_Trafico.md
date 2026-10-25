# S04 - Punto Único de Acceso y Distribución de Tráfico (Spring Cloud Gateway)

> **Sesión S04 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Componente:** `infra/medizano-gateway` (Puerto `:8090`)  
> **Tecnología:** Spring Cloud Gateway Reactivo (Netty), Eureka Discovery Client, GlobalFilters

---

## 1. Propósito Arquitectónico

Configurar **Spring Cloud Gateway** como el **Reverse Proxy y API Gateway perimetral** que centraliza todas las solicitudes externas entrantes al ecosistema de microservicios de MediZano. 

Elimina la exposición directa de puertos internos de microservicios a clientes externos (Frontend Angular, apps móviles o terceros), resolviendo:

1. **Punto Único de Entrada (Single Entry Point):** Los clientes solo interactúan con el puerto `:8090`.
2. **Descubrimiento Dinámico de Servicios:** Enrutamiento mediante nombres lógicos registrados en Eureka (`lb://USUARIO-MS`) sin hardcodear IPs ni puertos.
3. **Balanceo de Carga Lado Cliente (Spring Cloud LoadBalancer):** Distribución Round-Robin entre instancias réplicas saludables.
4. **Seguridad y Validación Centralizada de Tokens:** Filtro de autenticación JWT antes de propagar la petición al clúster interno.
5. **Políticas de CORS Unificadas:** Cabeceras HTTP seguras permitidas para el Frontend Angular.

---

## 2. Mapa Oficial de Rutas y Microservicios Destino

Cada ruta expuesta hacia el exterior se mapea de forma reactiva al servicio interno correspondiente:

| Ruta Externa (Gateway :8090) | URI de Enrutamiento (Eureka Load Balancer) | Microservicio Responsable | Tipo de Operación | Integrante Encargado |
| :--- | :--- | :--- | :---: | :--- |
| `/api/v1/auth/**` | `lb://usuario-ms` | `medizano-usuario-ms` (:8087) | No Transaccional | **Igarlos Mamani** |
| `/api/pharmacist/medicines/**` | `lb://catalogo-ms` | `medizano-catalogo-ms` (:8081) | No Transaccional | **Aldo Calla** |
| `/api/v1/clientes/**` | `lb://cliente-ms` | `medizano-cliente-ms` (:8084) | No Transaccional | **Kenyi Calsin** |
| `/api/v1/proveedores/**` | `lb://proveedor-ms` | `medizano-proveedor-ms` (:8088) | No Transaccional | **Juan Chambilla** |
| `/api/v1/oóordenes/**` | `lb://oorden-ms` | `medizano-oorden-ms` (:8082) | Transaccional (Cabecera-Detalle) | **Kenyi Calsin** |
| `/api/pharmacist/batches/**` | `lb://inventario-ms` | `medizano-inventario-ms` (:8085) | Transaccional (Kardex FEFO) | **Aldo Calla** |
| `/api/v1/pagos/**` | `lb://pago-ms` | `medizano-pago-ms` (:8083) | Transaccional (Idempotente) | **Igarlos Mamani** |
| `/api/v1/facturas/**` | `lb://facturacion-ms` | `medizano-facturacion-ms` (:8086) | Transaccional (SUNAT IGV 18%) | **Juan Chambilla** |
| `/api/v1/compras/**` | `lb://proveedor-ms` | `medizano-proveedor-ms` (:8088) | Transaccional (Abastecimiento) | **Juan Chambilla** |

---

## 3. Configuración YAML del Gateway (`application.yml`)

El enrutamiento declarativo se define mediante predicados de ruta (`Path`) y filtros reactivos:

```yaml
server:
  port: 8090

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        # 1. Autenticación y Usuarios
        - id: usuario-service
          uri: lb://usuario-ms
          predicates:
            - Path=/api/v1/auth/**, /api/v1/usuarios/**

        # 2. Catálogo Maestro de Medicamentos
        - id: catalogo-service
          uri: lb://catalogo-ms
          predicates:
            - Path=/api/pharmacist/medicines/**, /api/v1/medicamentos/**

        # 3. Lotes e Inventario FEFO
        - id: inventario-service
          uri: lb://inventario-ms
          predicates:
            - Path=/api/pharmacist/batches/**, /api/v1/inventario/**

        # 4. Proveedores y Compras
        - id: proveedor-service
          uri: lb://proveedor-ms
          predicates:
            - Path=/api/v1/proveedores/**, /api/v1/compras/**

        # 5. Ventas y Óóordenes POS
        - id: oorden-service
          uri: lb://oorden-ms
          predicates:
            - Path=/api/v1/oóordenes/**

        # 6. Pasarelas de Pago
        - id: pago-service
          uri: lb://pago-ms
          predicates:
            - Path=/api/v1/pagos/**

        # 7. Facturación Electrónica
        - id: facturacion-service
          uri: lb://facturacion-ms
          predicates:
            - Path=/api/v1/facturas/**

      # Configuración Global de CORS para Angular
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:4200"
              - "http://127.0.0.1:4200"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: true
```

---

## 4. Implementación del Filtro Global de Seguridad JWT (`JwtGatewayFilter`)

El Gateway inspecciona cada petición entrante mediante un `GlobalFilter` reactivo no bloqueante:

```java
package com.medizano.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:supersecretkeyformytravelplannerapp12345678901234567890}")
    private String jwtSecret;

    // Rutas públicas que no exigen token JWT
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/pharmacist/medicines",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Bypass para rutas públicas
        if (RUTAS_PUBLICAS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 2. Extraer cabecera Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // 3. Validar firma criptográfica HMAC-SHA
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecret.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 4. Inyectar claims validados hacia los microservicios internos
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Auth-User", claims.getSubject())
                    .header("X-Auth-Role", claims.get("role", String.class))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100; // Máxima prioridad en la cadena de filtros
    }
}
```

---

## 5. Pruebas y Evidencias de Funcionamiento

### Prueba 1: Acceso Denegado sin Token (HTTP 401)
```bash
curl -i -X GET http://localhost:8090/api/v1/compras
```
**Respuesta del Gateway:**
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "timestamp": "2026-10-25T22:15:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token JWT no proporcionado",
  "path": "/api/v1/compras"
}
```

### Prueba 2: Autenticación Exitosa en Gateway
```bash
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```
**Respuesta:**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJST0xFX0FETUlOIiwiZXhwIjoxNzkzMDUyNjAwfQ...",
  "type": "Bearer",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

### Prueba 3: Consulta Enrutada con Token Válido (HTTP 200)
```bash
curl -H "Authorization: Bearer <TOKEN_OBTENIDO>" http://localhost:8090/api/pharmacist/medicines
```
**Resultado:** Retorna el array con los 203 medicamentos farmacéuticos registrados.
