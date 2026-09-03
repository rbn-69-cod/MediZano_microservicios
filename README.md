# MediZano POS - Arquitectura de Microservicios, Observabilidad y Despliegue Docker

Sistema empresarial integral de Punto de Venta (POS) y Gestión Farmacéutica construido bajo una arquitectura distribuida de microservicios contenerizados con **Docker Compose**, descubrimiento dinámico con **Spring Cloud Netflix Eureka**, seguridad perimetral reactiva mediante **Spring Cloud Gateway** (JWT y RBAC), frontend contenerizado en **Angular 17** sobre **Nginx**, y una suite profesional de **Observabilidad** compuesta por **Spring Boot Actuator**, **Micrometer**, **Prometheus**, **Loki**, **Grafana Alloy** y **Grafana**.

---

## 1. Arquitectura General del Sistema

```
                       ┌─────────────────────────────────────────┐
                       │     Frontend Angular 17 (Nginx)         │ (:4200)
                       └────────────────────┬────────────────────┘
                                            │ HTTP / REST
                                            ▼
                       ┌─────────────────────────────────────────┐
                       │   Spring Cloud API Gateway (Perímetro)  │ (:8090)
                       └────────────────────┬────────────────────┘
                                            │ Red Interna Docker (medizano-net)
            ┌───────────────────────────────┼───────────────────────────────┐
            ▼                               ▼                               ▼
 ┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
 │     usuario-ms      │         │     catalogo-ms     │         │      orden-ms       │
 │   (:8087 interno)   │         │   (:8081 interno)   │         │   (:8082 interno)   │
 └──────────┬──────────┘         └──────────┬──────────┘         └──────────┬──────────┘
            ▼                               ▼                               ▼
 ┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
 │     cliente-ms      │         │    inventario-ms    │         │       pago-ms       │
 │   (:8084 interno)   │         │   (:8085 interno)   │         │   (:8083 interno)   │
 └──────────┬──────────┘         └──────────┬──────────┘         └──────────┬──────────┘
            ▼                               ▼
 ┌─────────────────────┐         ┌─────────────────────┐
 │   facturacion-ms    │         │  PostgreSQL Master  │ (:5432 interno)
 │   (:8086 interno)   │         │ (Multi-Schema POS)  │
 └──────────┬──────────┘         └─────────────────────┘
            │
            ├────────────────────────────────────────────────┐
            ▼                                                ▼
 ┌─────────────────────┐                          ┌─────────────────────┐
 │    Eureka Server    │ (:8761)                  │ Spring Boot Actuator│
 │  Service Discovery  │                          │    & Micrometer     │
 └─────────────────────┘                          └──────────┬──────────┘
                                                             │ Pull /actuator/prometheus
                                                             ▼
 ┌─────────────────────────────────────────┐      ┌─────────────────────┐
 │ Docker Engine Socket (/var/run/docker)  │      │     Prometheus      │ (:9090)
 └────────────────────┬────────────────────┘      └──────────┬──────────┘
                      │                                      │
                      ▼                                      │
 ┌─────────────────────────────────────────┐                 │
 │        Grafana Alloy (Collector)        │                 │
 └────────────────────┬────────────────────┘                 │
                      │ Push Logs                            │
                      ▼                                      │
 ┌─────────────────────────────────────────┐                 │
 │       Grafana Loki (Log Engine)         │ (:3100 interno) │
 └────────────────────┬────────────────────┘                 │
                      │                                      │
                      └──────────────────┬───────────────────┘
                                         ▼
                       ┌───────────────────────────────────┐
                       │     Grafana Analytics Platform    │ (:3000)
                       │ (Dashboards de Métricas y Logs)   │
                       └───────────────────────────────────┘
```

---

## 2. Aislamiento de Red y Cierre de Puertos al Host (Defensa Perimetral)

### ¿Por qué los microservicios de negocio (8081-8087) NO están publicados al Host?
En versiones anteriores, los puertos `8081` a `8087` estaban expuestos al host (`0.0.0.0:808x->808x`), lo que introducía una **vulnerabilidad crítica de bypass**: cualquier usuario o atacante en la red local podía invocar directamente un microservicio (por ejemplo `http://localhost:8081/api/pharmacist/medicines`) evadiendo completamente:
1. La autenticación de tokens JWT.
2. La autorización basada en roles (RBAC).
3. Las políticas de CORS y validación de cabeceras.
4. El registro de auditoría centralizado en el Gateway.

### Arquitectura Actual (Zero-Trust Interno):
- **Un único punto de entrada público**: El **API Gateway (`:8090`)**.
- Los microservicios de negocio se ejecutan aislados dentro del puente virtual de Docker (`medizano-net`).
- Ningún puerto `8081`..`8087` está vinculado al host.
- Prometheus y Eureka resuelven los microservicios internamente mediante DNS de Docker (`http://catalogo-ms:8081/actuator/prometheus`).
- Las bases de datos PostgreSQL (`5432`) y Loki (`3100`) son igualmente internas.

---

## 3. Suite de Observabilidad: Métricas, Logs y Dashboards

### 3.1. Métricas con Prometheus y Micrometer
Cada microservicio incorpora `spring-boot-starter-actuator` y `micrometer-registry-prometheus`, configurado para exponer únicamente los endpoints necesarios:
- `/actuator/health` (utilizado por los healthchecks de Docker).
- `/actuator/info`.
- `/actuator/prometheus` (consultado cada 5 segundos por Prometheus).

Métricas clave recolectadas:
- **Salud**: `up` (1 para saludable, 0 para caído).
- **HTTP**: `http_server_requests_seconds_count` clasificado por `service`, `uri`, `method` y `status` (2xx, 4xx, 5xx).
- **Latencia**: Tasa y percentiles de tiempo de respuesta (`http_server_requests_seconds_sum`).
- **Recursos JVM**: Memoria Heap (`jvm_memory_used_bytes{area="heap"}`), hilos activos y consumo de CPU (`process_cpu_usage`).

### 3.2. Logs Centralizados con Grafana Alloy y Loki
- **Decisión Técnica**: Grafana Labs oficializó la deprecación de Promtail. En su lugar, MediZano implementa **Grafana Alloy** (`v1.1.0`), el recolector unificado basado en OpenTelemetry.
- Alloy monta el socket de Docker (`/var/run/docker.sock`), descubre automáticamente los 15 contenedores, etiqueta sus streams con `{service="<nombre-microservicio>"}` y los envía a Loki (`:3100`).
- **Consultas LogQL**: Se pueden inspeccionar logs en Grafana filtrando por microservicio, por ejemplo:
  ```logql
  {service="api-gateway"} |= "WARN"
  {service="facturacion-ms"} |= "Venta registrada"
  ```

### 3.3. Visualización con Grafana Auto-Aprovisionado
Al iniciar el contenedor, Grafana carga automáticamente:
- **Datasource Prometheus**: `http://prometheus:9090` (Métricas).
- **Datasource Loki**: `http://loki:3100` (Logs).
- **Dashboard MediZano**: Ubicado en `/d/medizano-observability-overview`, con visualizaciones de servicios UP/DOWN, RPS por servicio, latencias promedio, uso de memoria JVM y visor de logs unificado.

---

## 4. Manejo de Caídas: HTTP 503 Service Unavailable

El API Gateway implementa `GlobalGatewayExceptionHandler` (`@Order(-1)`), garantizando que si un microservicio downstream se cae o se desconecta de Eureka:
1. El Gateway intercepta la excepción de conexión (`ConnectException`, `NotFoundException`, etc.).
2. Devuelve de inmediato una respuesta estructurada **`HTTP 503 Service Unavailable`**:
   ```json
   {
     "timestamp": "2026-09-03T22:47:14.410Z",
     "status": 503,
     "error": "Service Unavailable",
     "service": "catalogo-ms",
     "path": "/api/pharmacist/medicines",
     "message": "El microservicio solicitado se encuentra temporalmente no disponible"
   }
   ```
3. **Cero exposición de datos sensibles**: No se muestran trazas de código Java ni páginas HTML de error.
4. Prometheus detecta de inmediato el estado `down` (`up == 0`) y Grafana refleja la alerta en tiempo real.

---

## 5. Mapeo de Puertos

| Servicio | Puerto Host | Puerto Contenedor | Acceso |
|---|:---:|:---:|---|
| `frontend` (Angular 17 + Nginx) | **4200** | 80 | Público (Navegador) |
| `api-gateway` | **8090** | 8090 | Público (REST API) |
| `eureka-server` | **8761** | 8761 | Administrativo / Discovery |
| `prometheus` | **9090** | 9090 | Administrativo / Métricas |
| `grafana` | **3000** | 3000 | Administrativo / Dashboards |
| `usuario-ms` | *(Sin puerto host)* | 8087 | Interno (`medizano-net`) |
| `catalogo-ms` | *(Sin puerto host)* | 8081 | Interno (`medizano-net`) |
| `cliente-ms` | *(Sin puerto host)* | 8084 | Interno (`medizano-net`) |
| `orden-ms` | *(Sin puerto host)* | 8082 | Interno (`medizano-net`) |
| `inventario-ms` | *(Sin puerto host)* | 8085 | Interno (`medizano-net`) |
| `pago-ms` | *(Sin puerto host)* | 8083 | Interno (`medizano-net`) |
| `facturacion-ms` | *(Sin puerto host)* | 8086 | Interno (`medizano-net`) |
| `postgres` | *(Sin puerto host)* | 5432 | Interno (`medizano-net`) |
| `loki` | *(Sin puerto host)* | 3100 | Interno (`medizano-net`) |
| `alloy` | *(Sin puerto host)* | *(Socket)* | Interno (`medizano-net`) |

---

## 6. Despliegue de Todo el Ecosistema

### 6.1. Levantar Todo (15 Contenedores)
```bash
docker compose up -d --build
```
> **Nota**: No es necesario ejecutar ningún comando adicional manual (`ng serve`, `npm start` ni `java -jar`). Todo el ecosistema levanta contenerizado.

### 6.2. Comprobar Contenedores
```bash
docker compose ps
```

### 6.3. Detener Todo
```bash
docker compose down
```

---

## 7. Usuarios y Credenciales

### Credenciales de la Aplicación POS (Base de Datos)
| Usuario | Contraseña | Rol | Permisos |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Acceso total: Usuarios, Auditoría, Reportes |
| `cajero` | `admin123` | `CASHIER` | Ventas POS, Cobranzas, Emisión de Comprobantes |
| `inventario` | `admin123` | `STOCK_MONITOR` | Medicamentos, Lotes, Control de Vencimientos |

### Credenciales de Grafana
- **URL**: [http://localhost:3000](http://localhost:3000)
- **Usuario**: Configurable vía variable de entorno `GRAFANA_ADMIN_USER` (por defecto `admin`).
- **Contraseña**: Configurable vía variable de entorno `GRAFANA_ADMIN_PASSWORD` (por defecto `admin123`).
