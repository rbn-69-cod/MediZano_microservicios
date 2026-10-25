# S03 - Registro y Descubrimiento Dinámico de Servicios (Spring Cloud Netflix Eureka)

> **Sesión S03 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Componente:** `infra/medizano-eureka` (Puerto `:8761`)  
> **Tecnología:** Netflix Eureka Server, Heartbeats (30s), Self-Preservation Mode

---

## 1. Propósito Arquitectónico

En un ecosistema distribuido donde los microservicios se ejecutan dentro de contenedores Docker con direcciones IP dinámicas y efímeras, es inviable configurar URLs estáticas en cada aplicación.

**Eureka Server** actúa como el **Directorio Telefónico Centralizado (Service Registry)** del clúster:
1. Cada microservicio, al arrancar, se registra automáticamente enviando su nombre de aplicación (`spring.application.name`), IP interna y puerto.
2. Los clientes (API Gateway y clientes OpenFeign) consultan a Eureka para resolver nombres lógicos como `lb://CATALOGO-MS` o `lb://INVENTARIO-MS`.
3. Mantiene monitoreo continuo de salud mediante **Heartbeats periódicos (cada 30 segundos)**. Si un servicio se cae o no responde en 90 segundos, Eureka lo expulsa del registro para que el Gateway deje de enviarle tráfico.

---

## 2. Configuración del Servidor Eureka (`application.yml`)

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: eureka-server
  client:
    # Como es el servidor central, no debe registrarse a sí mismo
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    enable-self-preservation: true
    eviction-interval-timer-in-ms: 10000
```

---

## 3. Configuración de los Microservicios Clientes (Eureka Clients)

Cada microservicio del sistema (`usuario-ms`, `catalogo-ms`, `oorden-ms`, etc.) incluye la dependencia cliente:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Y en su archivo `application.yml` declara su registro:

```yaml
spring:
  application:
    name: catalogo-ms

eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

---

## 4. Estado en Vivo del Registro Eureka (8 Microservicios Registrados)

Al consultar el panel de control web en `http://localhost:8761/`, se observan todas las instancias registradas y en estado **UP**:

| Application ID | AMIs / Contenedor | Disponibilidad | Puerto Interno |
| :--- | :--- | :---: | :---: |
| **`API-GATEWAY`** | `medizano-api-gateway:8090` | 🟢 UP (1) | `:8090` |
| **`USUARIO-MS`** | `medizano-usuario-ms:8087` | 🟢 UP (1) | `:8087` |
| **`CATALOGO-MS`** | `medizano-catalogo-ms:8081` | 🟢 UP (1) | `:8081` |
| **`CLIENTE-MS`** | `medizano-cliente-ms:8084` | 🟢 UP (1) | `:8084` |
| **`ORDEN-MS`** | `medizano-oorden-ms:8082` | 🟢 UP (1) | `:8082` |
| **`INVENTARIO-MS`** | `medizano-inventario-ms:8085` | 🟢 UP (1) | `:8085` |
| **`PAGO-MS`** | `medizano-pago-ms:8083` | 🟢 UP (1) | `:8083` |
| **`FACTURACION-MS`** | `medizano-facturacion-ms:8086` | 🟢 UP (1) | `:8086` |
| **`PROVEEDOR-MS`** | `medizano-proveedor-ms:8088` | 🟢 UP (1) | `:8088` |

---

## 5. Pruebas de Resiliencia y Desconexión

### Prueba: Desconexión de un Microservicio y Expulsión del Registro
```bash
# 1. Detener el microservicio de catálogo temporalmente
docker stop medizano-catalogo-ms

# 2. Consultar Eureka Server
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps/CATALOGO-MS
```
**Efecto:** Eureka detecta la falta de heartbeats y lo marca como `DOWN` o lo desregistra. El API Gateway, mediante su caché local de balanceador y Resilience4j, activa de inmediato la respuesta controlada de error (`HTTP 503 Service Unavailable`) evitando timeouts prolongados en el cliente.
