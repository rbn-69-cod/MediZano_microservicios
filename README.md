# MediZano POS - Arquitectura de Microservicios y Observabilidad

Sistema empresarial integral de Punto de Venta (POS) y Gestión Farmacéutica diseñado bajo una arquitectura distribuida de microservicios contenerizados en **Docker**, con descubrimiento dinámico (**Spring Cloud Netflix Eureka**), enrutamiento perimetral con seguridad JWT (**Spring Cloud Gateway**), persistencia multi-esquema (**PostgreSQL 16**) y una suite profesional de **Observabilidad** compuesta por **Spring Boot Actuator**, **Micrometer**, **Prometheus**, **Loki**, **Grafana Alloy** y **Grafana**.

---

## 1. Arquitectura del Sistema

```
                                  ┌────────────────────────┐
                                  │    Frontend Angular    │ (:4200)
                                  └───────────┬────────────┘
                                              │ HTTP / JSON
                                              ▼
                                  ┌────────────────────────┐
                                  │   Spring API Gateway   │ (:8090)
                                  └───────────┬────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
         ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
         │     usuario-ms      │   │     catalogo-ms     │   │      orden-ms       │
         │       (:8087)       │   │       (:8081)       │   │       (:8082)       │
         └──────────┬──────────┘   └──────────┬──────────┘   └──────────┬──────────┘
                    ▼                         ▼                         ▼
         ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
         │     cliente-ms      │   │    inventario-ms    │   │       pago-ms       │
         │       (:8084)       │   │       (:8085)       │   │       (:8083)       │
         └──────────┬──────────┘   └──────────┬──────────┘   └──────────┬──────────┘
                    ▼                         ▼
         ┌─────────────────────┐   ┌─────────────────────┐
         │   facturacion-ms    │   │  PostgreSQL Master  │ (:5432)
         │       (:8086)       │   │ (Múltiples Schemas) │
         └──────────┬──────────┘   └─────────────────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │    Eureka Server    │ (:8761 - Service Discovery)
         └─────────────────────┘
```

---

## 2. Diferenciación Conceptual de Componentes

Para efectos académicos y profesionales, cada herramienta en la infraestructura cumple un rol específico y complementario:

| Componente | Capa / Rol | Propósito Principal |
|---|---|---|
| **Eureka Server** | Service Discovery | Permite el registro dinámico de instancias y el balanceo de carga interno (`lb://`) sin codificar IPs fijas. |
| **API Gateway** | Perimeter Gateway & Security | Punto de entrada único para el frontend. Valida JWT (`JwtGatewayFilter`), RBAC y preflights CORS. |
| **Spring Boot Actuator** | Instrumentation Layer | Expone datos de salud interna (`/actuator/health`) y telemetría de la JVM. |
| **Micrometer** | Metrics Facade | Abstrae y recolecta métricas de JVM, CPU, latencias HTTP y peticiones en formato compatible con Prometheus. |
| **Prometheus** | Time-Series Metrics DB | Realiza *scraping* periódico de `/actuator/prometheus` en cada microservicio y almacena series temporales. |
| **Grafana Alloy** | Unified Telemetry Collector | Recolector moderno de Grafana Labs (sucesor de Promtail). Lee los logs desde el Docker socket y los envía a Loki. |
| **Loki** | Log Aggregation Engine | Motor de indexación y almacenamiento centralizado de logs, optimizado para metadatos y etiquetas. |
| **Grafana** | Unified Visualization | Plataforma central donde se visualizan tableros unificados con métricas de Prometheus y logs de Loki. |

---

## 3. Observabilidad

La observabilidad en MediZano se fundamenta en los tres pilares de la ingeniería moderna de software: **Métricas**, **Logs** y **Visualización**.

```
[Microservicios Spring Boot]
      │
      ├─► /actuator/prometheus ──(Pull cada 5s)──► [ Prometheus (:9090) ] ────┐
      │                                                                       ▼
      └─► Docker Stdout / Stderr ──► [ Grafana Alloy ] ──► [ Loki (:3100) ] ─► [ Grafana (:3000) ]
```

### 3.1. Métricas (Prometheus + Micrometer)
- **JVM**: Heap & Non-Heap Memory (`jvm_memory_used_bytes`), recolección de basura GC, conteo de hilos activos (`jvm_threads_live_threads`).
- **Sistema**: Consumo de CPU del proceso y sistema (`process_cpu_usage`, `system_cpu_usage`).
- **HTTP**: Cantidad de peticiones, latencias media y percentil 95 (`http_server_requests_seconds_count`, `http_server_requests_seconds_sum`).
- **Códigos de Estado**: Clasificación de respuestas 2xx, 4xx (errores de cliente / 401 Unauthorized) y 5xx (fallas de servidor).

### 3.2. Logs Centralizados (Loki + Grafana Alloy)
- **Decisión Técnica Grafana Alloy**: Grafana Labs anunció la **deprecación oficial de Promtail**. Por tanto, MediZano implementa **Grafana Alloy**, configurado mediante componentes dinámicos de River (`discovery.docker`, `discovery.relabel`, `loki.source.docker` y `loki.write`).
- **Etiquetado Automático**: Cada línea de log emitida a `stdout`/`stderr` por los contenedores Docker es capturada e indexada con etiquetas:
  - `{service="api-gateway"}`
  - `{service="usuario-ms"}`
  - `{service="facturacion-ms"}`
  - `{service="pago-ms"}`
  - etc.

### 3.3. Visualización (Grafana Dashboards Auto-Aprovisionados)
Al iniciar Docker, Grafana se auto-configura mediante archivos de aprovisionamiento en `monitoring/grafana/provisioning/`:
- **Datasources preconfigurados**:
  - `Prometheus` (default, `http://prometheus:9090`)
  - `Loki` (`http://loki:3100`)
- **Dashboard Incluido**: `MediZano POS - Dashboard de Observabilidad` (`/d/medizano-observability-overview`), con paneles de:
  - Cantidad de servicios UP / DOWN en tiempo real.
  - Tasa de solicitudes por segundo (RPS) por servicio.
  - Latencia promedio por microservicio.
  - Memoria Heap utilizada en megabytes.
  - Logs en vivo con filtrado dinámico.

---

## 4. Mapeo de Puertos del Ecosistema

| Servicio / Contenedor | Puerto Host | Descripción |
|---|:---:|---|
| `frontend` (Angular 17) | **4200** | Interfaz de Usuario del POS Farmacéutico |
| `api-gateway` | **8090** | Puerta de enlace única y seguridad JWT/CORS |
| `eureka-server` | **8761** | Panel de Descubrimiento de Servicios Spring Cloud |
| `catalogo-ms` | **8081** | Catálogo de productos y medicamentos |
| `orden-ms` | **8082** | Gestión de órdenes de compra |
| `pago-ms` | **8083** | Pasarelas de pago (PayPal Sandbox y Mercado Pago) |
| `cliente-ms` | **8084** | Directorio de clientes |
| `inventario-ms` | **8085** | Control de lotes, vencimientos y stock |
| `facturacion-ms` | **8086** | Facturación POS, boletas, notas y reportes de caja |
| `usuario-ms` | **8087** | Autenticación, JWT, roles y auditoría |
| `postgres` | **5432** | Base de datos relacional multi-esquema |
| `prometheus` | **9090** | Servidor de métricas y consultas PromQL |
| `loki` | **3100** | Servidor de ingesta y consulta LogQL |
| `grafana` | **3000** | Plataforma de tableros y analítica |
| `grafana-alloy` | *(Interno)* | Agente colector de logs desde el Docker socket |

---

## 5. Instrucciones de Despliegue con Docker Compose

### Requisitos Previos
- Docker Desktop (con integración WSL2 o Hyper-V activa)
- Docker Compose v2+
- Node.js 18+ (para el frontend Angular local)

### Iniciar Todo el Ecosistema
Desde la raíz del repositorio oficial:
```bash
# Construir y levantar los 14 contenedores en segundo plano
docker compose up -d --build
```

### Verificar el Estado de Salud
```bash
docker compose ps
```
Todos los contenedores deben reportar estado `healthy` o `running`.

### Detener el Ecosistema
```bash
docker compose down
# Para eliminar volúmenes y reiniciar datos desde cero:
docker compose down -v
```

---

## 6. Usuarios y Credenciales de Prueba

| Usuario | Contraseña | Rol Asignado | Alcance y Permisos |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Acceso irrestricto: usuarios, auditoría, reportes de ventas y caja |
| `cajero` | `admin123` | `CASHIER` | Emisión de ventas POS, cobranzas, devoluciones |
| `inventario`| `admin123` | `STOCK_MONITOR`| Control de lotes, alertas de vencimiento, catálogo farmacéutico |

### Credenciales de Grafana
- **URL**: `http://localhost:3000`
- **Usuario**: `admin`
- **Contraseña**: `admin`

---

## 7. URLs Principales del Sistema

- **Frontend Angular POS**: [http://localhost:4200](http://localhost:4200)
- **API Gateway**: [http://localhost:8090](http://localhost:8090)
- **Panel Eureka Server**: [http://localhost:8761](http://localhost:8761)
- **Prometheus UI**: [http://localhost:9090](http://localhost:9090)
- **Prometheus Targets Status**: [http://localhost:9090/targets](http://localhost:9090/targets)
- **Loki Readiness Check**: [http://localhost:3100/ready](http://localhost:3100/ready)
- **Grafana Dashboards**: [http://localhost:3000](http://localhost:3000)
- **Dashboard MediZano**: [http://localhost:3000/d/medizano-observability-overview](http://localhost:3000/d/medizano-observability-overview)

---

## 8. Verificación de Seguridad y Git

- **Variables Sensibles**: No se incluyen contraseñas reales de producción, tokens de acceso privados ni llaves criptográficas en el control de versiones.
- **Protección**: Las variables críticas están parametrizadas en `docker-compose.yml` utilizando valores por defecto para pruebas locales (`${VAR:-default}`).
