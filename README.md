# MediZano POS - Arquitectura de Microservicios, Observabilidad y Despliegue Docker

Sistema empresarial integral de Punto de Venta (POS) y Gestión Farmacéutica construido bajo una arquitectura distribuida de microservicios nativa de la nube, contenerizada completamente con **Docker Compose**, descubrimiento dinámico mediante **Spring Cloud Netflix Eureka**, seguridad reactiva perimetral con **Spring Cloud Gateway** (JWT y RBAC), frontend contenerizado en **Angular 17** sobre **Nginx** con **Reverse Proxy**, y una suite profesional de **Observabilidad** compuesta por **Spring Boot Actuator**, **Micrometer**, **Prometheus**, **Loki**, **Grafana Alloy** y **Grafana**.

---

## 1. Arquitectura General del Sistema

### Diagrama de Bloques (ASCII)

```
                        ┌─────────────────────────────────────────┐
                        │     Frontend Angular 17 (Nginx)         │ (:4200)
                        │      Reverse Proxy: /api/ -> :8090      │
                        └────────────────────┬────────────────────┘
                                             │ HTTP / REST (Same-Origin)
                                             ▼
                        ┌─────────────────────────────────────────┐
                        │   Spring Cloud API Gateway (Perímetro)  │ (:8090)
                        │     (JWT Auth, RBAC & 503 Fallback)     │
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

### Diagrama Mermaid

```mermaid
graph TD
    User([Navegador Web / Cliente]) -->|HTTP :4200| Frontend[Frontend Angular 17 + Nginx]
    Frontend -->|Reverse Proxy /api/| Gateway[Spring Cloud API Gateway :8090]
    
    subgraph Red Interna Docker: medizano-net
        Gateway -->|lb://usuario-ms| MS_User[usuario-ms :8087]
        Gateway -->|lb://catalogo-ms| MS_Cat[catalogo-ms :8081]
        Gateway -->|lb://cliente-ms| MS_Cli[cliente-ms :8084]
        Gateway -->|lb://orden-ms| MS_Ord[orden-ms :8082]
        Gateway -->|lb://inventario-ms| MS_Inv[inventario-ms :8085]
        Gateway -->|lb://facturacion-ms| MS_Fac[facturacion-ms :8086]
        Gateway -->|lb://pago-ms| MS_Pag[pago-ms :8083]
        
        MS_User & MS_Cat & MS_Cli & MS_Ord & MS_Inv & MS_Fac & MS_Pag -->|JDBC| Postgres[(PostgreSQL 16 Multi-DB :5432)]
        MS_User & MS_Cat & MS_Cli & MS_Ord & MS_Inv & MS_Fac & MS_Pag & Gateway -.->|Registro y Descubrimiento| Eureka[Eureka Server :8761]
        
        MS_Fac -.->|Feign Client| MS_Inv
        MS_Fac -.->|Feign Client| MS_Cat
        MS_Fac -.->|Feign Client| MS_Pag
        MS_Ord -.->|Feign Client| MS_Cat
        MS_Ord -.->|Feign Client| MS_Inv
        
        Prometheus[Prometheus :9090] -->|Scrape /actuator/prometheus| MS_User & MS_Cat & MS_Cli & MS_Ord & MS_Inv & MS_Fac & MS_Pag & Gateway & Eureka
        Alloy[Grafana Alloy] -->|Lee Docker Socket| DockerEngine[(Docker Socket)]
        Alloy -->|Push Logs| Loki[Grafana Loki :3100]
        Grafana[Grafana Dashboards :3000] -->|Consulta Métricas| Prometheus
        Grafana -->|Consulta Logs| Loki
    end
```

---

## 2. Inventario de Microservicios

| Microservicio | Puerto Interno | Base de Datos | Stack Tecnológico | Responsabilidad |
|---|:---:|---|---|---|
| **api-gateway** | `8090` | N/A | Spring Cloud Gateway, Reactive, JWT, Micrometer | Perímetro de seguridad, validación JWT, RBAC, enrutamiento dinámico y respuesta estructurada 503 ante fallos. |
| **eureka-server** | `8761` | N/A | Spring Cloud Netflix Eureka Server | Registro de instancias y resolución de nombres dinámica (`lb://<service-name>`). |
| **usuario-ms** | `8087` | `medizano_usuarios_db` | Spring Boot 3, Spring Security, JWT, JPA, Flyway/Ddl | Autenticación, generación y validación de tokens JWT, gestión de usuarios y auditoría de accesos. |
| **catalogo-ms** | `8081` | `medizano_catalogo_db` | Spring Boot 3, Spring Data JPA, Hibernate | Catálogo de productos, medicamentos, fabricantes, control de prescripción y búsqueda por código de barras. |
| **cliente-ms** | `8084` | `medizano_clientes_db` | Spring Boot 3, Spring Data JPA, Hibernate | Directorio y fidelización de clientes para facturación y ventas POS. |
| **inventario-ms** | `8085` | `medizano_inventario_db` | Spring Boot 3, Spring Data JPA, Hibernate | Gestión de lotes farmacéuticos, fechas de caducidad, stock disponible y alertas de bajo inventario. |
| **orden-ms** | `8082` | `medizano_ordenes_db` | Spring Boot 3, Spring Data JPA, OpenFeign | Órdenes de compra transaccionales y reservas de stock. |
| **facturacion-ms** | `8086` | `medizano_facturacion_db` | Spring Boot 3, Spring Data JPA, OpenFeign | Generación de comprobantes de venta (boletas/facturas), cálculo de impuestos (IGV/GST), devoluciones y reportes analíticos. |
| **pago-ms** | `8083` | `medizano_pagos_db` | Spring Boot 3, Spring Data JPA, HTTP Client | Procesamiento de cobros multimoneda, integración con PayPal Sandbox y Mercado Pago Checkout Pro / Webhooks. |
| **frontend** | `80` *(expuesto :4200)* | N/A | Angular 17, TypeScript, Bootstrap 5, Nginx Alpine | Interfaz web de usuario (POS, Gestión de Medicamentos, Facturación, Devoluciones y Reportes). |

---

## 3. Frontend Angular 17 Contenerizado con Nginx Reverse Proxy

### 3.1. Arquitectura de Despliegue del Frontend
El frontend se ejecuta en un contenedor Nginx Alpine ultraligero que cumple dos funciones clave:
1. **Servidor Web de Contenido Estático**: Entrega los artefactos compilados de la Single Page Application (SPA) de Angular 17 con compresión y caché optimizada.
2. **Reverse Proxy hacia el API Gateway**: Nginx intercepta todas las solicitudes hacia `/api/` y las reenvía internamente por la red Docker a `http://api-gateway:8090/api/`.

### 3.2. Configuración de Nginx (`frontend/nginx.conf`)
```nginx
server {
    listen 80;
    listen [::]:80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }

    # Proxy inverso para microservicios a través del API Gateway
    location /api/ {
        proxy_pass http://api-gateway:8090/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ~* \.(?:ico|css|js|gif|jpe?g|png|woff2?|eot|ttf|svg)$ {
        root /usr/share/nginx/html;
        expires 1y;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
```

### 3.3. Configuración de Angular (`environment.prod.ts` y `environment.ts`)
```typescript
export const environment = {
  production: true,
  apiUrl: '/api'
};
```

### 3.4. Ventajas Fundamentales de Esta Arquitectura
- **Eliminación Absoluta de Problemas CORS**: Como el navegador solicita `http://localhost:4200/api/...`, la petición es **Same-Origin** con respecto a la aplicación cargada (`http://localhost:4200/`).
- **Independencia de Host y Portabilidad Total**: No existen URLs absolutas tipo `http://localhost:8090` hardcodeadas en el código compilado. Al desplegar MediZano en un servidor con IP estática o dominio (`http://192.168.1.100:4200` o `https://pos.medizano.com`), la aplicación funciona de inmediato sin necesidad de recompilar.
- **Seguridad en Red Privada**: Los microservicios no requieren exponer puertos adicionales al host del usuario.

---

## 4. Diagnóstico y Resolución del Error de Conexión en Frontend

### Causa Raíz Identificada
Al abrir `http://localhost:4200`, el frontend presentaba el mensaje:
> *"No se pudo conectar con el servidor. Verifica que el backend esté encendido."*

Tras la auditoría técnica profunda, se identificaron **dos causas reales concurrentes**:
1. **Conflicto de Cabeceras CORS Duplicadas**: Tanto el `api-gateway` (vía `spring.cloud.gateway.globalcors`) como `usuario-ms` (vía `SecurityConfig.java`) agregaban cabeceras `Access-Control-Allow-Origin: http://localhost:4200`. La especificación W3C/Fetch prohíbe múltiples valores para dicha cabecera. Los navegadores modernos (Chrome, Edge, Firefox) abortan la solicitud por política CORS estricta y retornan código `status: 0` al cliente HTTP de Angular.
2. **Ausencia de Reverse Proxy en el Contenedor Nginx**: Nginx no contaba con una regla `location /api/`, por lo que cualquier petición POST interna al puerto 4200 recibía `HTTP 405 Method Not Allowed`.

### Solución Definitiva Implementada
1. **Nginx Reverse Proxy Configurado**: Nginx ahora enruta `/api/` a `api-gateway:8090/api/`.
2. **Deduplicación Automática en Gateway**: Se activó el filtro `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE` en Spring Cloud Gateway, previniendo cabeceras duplicadas.
3. **Mapeo Robusto de Errores en Angular (`api.service.ts`)**:
   - `0`: "No se pudo establecer conexión con el servidor (ERR_CONNECTION_REFUSED / Red). Verifica que los servicios estén activos."
   - `400`: "Solicitud incorrecta. Revisa los datos enviados."
   - `401`: "Usuario o contraseña incorrectos o sesión expirada."
   - `403`: "Acceso denegado: No cuentas con los permisos necesarios para realizar esta acción."
   - `404`: "El recurso solicitado no fue encontrado en el servidor."
   - `500`: "Error interno en el servidor. Por favor intenta más tarde."
   - `503`: "Servicio no disponible: El microservicio correspondiente se encuentra reiniciando o inaccesible."

---

## 5. Aislamiento de Red y Cierre de Puertos al Host (Zero-Trust)

### ¿Por qué los puertos 8081 a 8087 NO están vinculados al Host?
En arquitecturas vulnerables, vincular microservicios al host (`0.0.0.0:808x->808x`) permite a cualquier usuario evadir el API Gateway y consumir directamente la lógica interna sin autenticación JWT ni control de roles.

### Política de Red Implementada en Docker Compose
- **Único Puerto Público de Entrada Web**: `:4200` (Frontend Nginx) y opcionalmente `:8090` (API Gateway directo).
- **Puertos 8081 a 8087 (Microservicios)**: Configurados con `expose:` en lugar de `ports:`. Solo accesibles dentro del puente Docker `medizano-net`.
- **PostgreSQL (`:5432`) y Loki (`:3100`)**: Completamente internos a Docker.

---

## 6. Seguridad y Autenticación JWT + Control de Acceso (RBAC)

### Flujo de Autenticación
1. El cliente envía `POST /api/auth/login` con `{ username, password }`.
2. `usuario-ms` valida las credenciales encriptadas con BCrypt y genera un token JWT firmado con algoritmo HMAC-SHA256 (384-bit key).
3. El frontend almacena el token en `localStorage` y lo adjunta automáticamente en cada petición mediante `TokenInterceptor` (`Authorization: Bearer <token>`).
4. `api-gateway` y los microservicios downstream validan la firma, vigencia y roles del token en cada solicitud.

### Matriz de Roles y Permisos

| Rol | Rutas Autorizadas | Funcionalidad |
|---|---|---|
| `ADMIN` | `/api/admin/**`, `/api/pharmacist/**`, `/api/cashier/**`, `/api/v1/**` | Acceso irrestricto a usuarios, auditoría, catálogo, inventario, ventas y reportes. |
| `CASHIER` | `/api/cashier/bills/**`, `/api/cashier/returns/**`, `/api/cashier/paypal/**`, `/api/cashier/mercadopago/**` | Emisión de boletas/facturas, cobros y devoluciones de clientes. |
| `STOCK_MONITOR` | `/api/pharmacist/medicines/**`, `/api/pharmacist/batches/**`, `/api/v1/inventario/**` | Supervisión de stock, control de lotes y alertas de caducidad. |
| `STOCK_KEEPER` | `/api/pharmacist/medicines/**`, `/api/pharmacist/batches/**` | Registro de nuevos medicamentos y actualización de existencias. |
| `CUSTOMER_SUPPORT`| `/api/cashier/returns/**` | Gestión y validación de devoluciones de productos. |
| `ANALYST` / `MANAGER` | `/api/admin/reports/**`, `/api/v1/ordenes/**` | Consulta de reportes de ventas, recaudación y transacciones. |

---

## 7. Tolerancia a Fallos y Manejo de Caídas (HTTP 503)

El API Gateway cuenta con `GlobalGatewayExceptionHandler` (`@Order(-1)`). Si un microservicio individual (por ejemplo `catalogo-ms`) se detiene o se desconecta de Eureka:
1. El Gateway intercepta la excepción reactiva de conexión (`ConnectException`, `ResponseStatusException`).
2. Devuelve de inmediato una respuesta estructurada **`HTTP 503 Service Unavailable`**:
   ```json
   {
     "timestamp": "2026-09-03T23:11:45.840Z",
     "status": 503,
     "error": "Service Unavailable",
     "service": "catalogo-ms",
     "path": "/api/pharmacist/medicines",
     "message": "El microservicio solicitado se encuentra temporalmente no disponible"
   }
   ```
3. El frontend muestra al usuario un mensaje claro sin romper la aplicación.
4. Prometheus registra la caída (`up == 0`) y Grafana dispara las alertas correspondientes.

---

## 8. Suite de Observabilidad: Prometheus, Loki, Alloy y Grafana

### 8.1. Métricas con Prometheus y Micrometer
Cada microservicio Spring Boot expone `/actuator/prometheus`. Prometheus (`:9090`) recolecta periódicamente:
- **Tasa de Peticiones HTTP**: `rate(http_server_requests_seconds_count[1m])`
- **Tiempos de Respuesta (Percentiles)**: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))`
- **Memoria Heap JVM**: `jvm_memory_used_bytes{area="heap"}`
- **Hilos Activos**: `jvm_threads_live_threads`
- **Uso de CPU**: `process_cpu_usage`

### 8.2. Logs Centralizados con Grafana Alloy y Loki
- **Grafana Alloy (`v1.1.0`)**: Recolector oficial OpenTelemetry de Grafana Labs que reemplaza a Promtail. Lee los logs directamente del socket de Docker (`/var/run/docker.sock`), extrae metadatos del contenedor y los despacha a Loki (`:3100`).
- **Consultas LogQL Útiles en Grafana**:
  ```logql
  # Ver errores en el API Gateway
  {service="api-gateway"} |= "ERROR"
  
  # Rastrear ventas emitidas en Facturación
  {service="facturacion-ms"} |= "Venta registrada"
  
  # Supervisar accesos y login en Usuario-MS
  {service="usuario-ms"} |= "Inicio de sesión"
  ```

### 8.3. Dashboards Auto-Aprovisionados en Grafana
Grafana (`:3000`) se inicializa automáticamente con:
- Datasource Prometheus (`http://prometheus:9090`)
- Datasource Loki (`http://loki:3100`)
- Dashboard de Visión General MediZano (`/d/medizano-observability-overview`)

---

## 9. Healthchecks Estrictos de Docker (Sin `exit 0` Falsos)

Todos los contenedores en `docker-compose.yml` implementan healthchecks reales que validan el estado activo del servicio (`status: UP`):

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q -O - http://localhost:8087/actuator/health | grep -q 'UP' || exit 1"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 25s
```

| Contenedor | Comando de Verificación | Condición Saludable |
|---|---|---|
| `medizano-frontend` | `wget -q -O - http://127.0.0.1/ \|\| exit 1` | Retorna HTML 200 |
| `medizano-api-gateway` | `wget -q -O - http://localhost:8090/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-eureka-server` | `wget -q -O - http://localhost:8761/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-usuario-ms` | `wget -q -O - http://localhost:8087/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-catalogo-ms` | `wget -q -O - http://localhost:8081/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-cliente-ms` | `wget -q -O - http://localhost:8084/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-orden-ms` | `wget -q -O - http://localhost:8082/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-inventario-ms`| `wget -q -O - http://localhost:8085/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-pago-ms` | `wget -q -O - http://localhost:8083/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-facturacion-ms`| `wget -q -O - http://localhost:8086/actuator/health \| grep -q 'UP' \|\| exit 1` | Retorna `{"status":"UP"}` |
| `medizano-postgres` | `pg_isready -U postgres` | Postgres listo para conexiones |
| `medizano-prometheus` | `wget -q -O - http://localhost:9090/-/healthy \| grep -q 'Prometheus Server is Healthy.' \|\| exit 1` | Prometheus listo |
| `medizano-loki` | `wget -q -O - http://localhost:3100/ready \| grep -q 'ready' \|\| exit 1` | Loki listo |
| `medizano-grafana` | `wget -q -O - http://localhost:3000/api/health \| grep -q 'ok' \|\| exit 1` | Grafana listo |

---

## 10. Mapeo de Puertos del Ecosistema

| Servicio | Puerto Host | Puerto Contenedor | Tipo de Acceso |
|---|:---:|:---:|---|
| **medizano-frontend** | **4200** | 80 | Público (Navegador Web) |
| **medizano-api-gateway** | **8090** | 8090 | Público (REST API) |
| **medizano-eureka-server** | **8761** | 8761 | Administrativo (Discovery Dashboard) |
| **medizano-prometheus** | **9090** | 9090 | Administrativo (Métricas y PromQL) |
| **medizano-grafana** | **3000** | 3000 | Administrativo (Dashboards y Logs) |
| **medizano-usuario-ms** | *(Sin vincular)* | 8087 | Red Interna Docker (`medizano-net`) |
| **medizano-catalogo-ms** | *(Sin vincular)* | 8081 | Red Interna Docker (`medizano-net`) |
| **medizano-cliente-ms** | *(Sin vincular)* | 8084 | Red Interna Docker (`medizano-net`) |
| **medizano-orden-ms** | *(Sin vincular)* | 8082 | Red Interna Docker (`medizano-net`) |
| **medizano-inventario-ms** | *(Sin vincular)* | 8085 | Red Interna Docker (`medizano-net`) |
| **medizano-pago-ms** | *(Sin vincular)* | 8083 | Red Interna Docker (`medizano-net`) |
| **medizano-facturacion-ms**| *(Sin vincular)* | 8086 | Red Interna Docker (`medizano-net`) |
| **medizano-postgres** | *(Sin vincular)* | 5432 | Red Interna Docker (`medizano-net`) |
| **medizano-loki** | *(Sin vincular)* | 3100 | Red Interna Docker (`medizano-net`) |
| **medizano-alloy** | *(Sin vincular)* | *(Socket)* | Red Interna Docker (`medizano-net`) |

---

## 11. Variables de Entorno y Secretos (`.env.example`)

Cree un archivo `.env` en la raíz del proyecto para sobreescribir las credenciales por defecto:

```ini
# --- Base de Datos PostgreSQL ---
POSTGRES_DB=medizano_master_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_postgres_password_here

# --- Seguridad y Autenticación JWT ---
JWT_SECRET=YourSuperSecretKeyForJWTTokensMustBeAtLeast256BitsLong2026
MEDIZANO_DEFAULT_PASSWORD=admin123

# --- Pasarela PayPal Sandbox ---
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=your_paypal_sandbox_client_id_here
PAYPAL_CLIENT_SECRET=your_paypal_sandbox_client_secret_here
PAYPAL_CURRENCY=USD
PAYPAL_EXCHANGE_RATE=3.75

# --- Pasarela Mercado Pago ---
MERCADOPAGO_ACCESS_TOKEN=TEST-your-mercadopago-access-token-here
MERCADOPAGO_PUBLIC_KEY=TEST-your-mercadopago-public-key-here
MERCADOPAGO_BASE_URL=https://api.mercadopago.com
MERCADOPAGO_CURRENCY=PEN
MERCADOPAGO_WEBHOOK_SECRET=your_mp_webhook_secret_key_here

# --- Suite de Observabilidad: Grafana ---
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=your_grafana_secure_admin_password_here
```

---

## 12. Guía de Despliegue Rápido

### Requisitos Previos
- Docker Engine 24+ y Docker Compose v2+
- Git

### 12.1. Clonar y Levantar Todo (15 Contenedores)
```bash
git clone https://github.com/rbn-69-cod/MediZano_microservicios.git
cd MediZano_microservicios

# Levantar toda la infraestructura contenerizada
docker compose up -d --build
```
> **Nota de Producción**: No se requiere instalar Node.js, Java ni Maven en la máquina anfitriona. Todo el proceso de empaquetado y ejecución corre en contenedores Docker aislados.

### 12.2. Verificar Salud de los Servicios
```bash
docker compose ps
```
Debe visualizar los 15 contenedores en estado `Up (healthy)`.

### 12.3. Acceder a las Aplicaciones
- **Frontend POS MediZano**: [http://localhost:4200](http://localhost:4200)
- **API Gateway REST**: [http://localhost:8090](http://localhost:8090)
- **Eureka Service Registry**: [http://localhost:8761](http://localhost:8761)
- **Prometheus Metrics**: [http://localhost:9090](http://localhost:9090)
- **Grafana Analytics**: [http://localhost:3000](http://localhost:3000)

### 12.4. Detener el Ecosistema
```bash
docker compose down
```

---

## 13. Usuarios y Credenciales por Defecto

### Usuarios de la Aplicación (Base de Datos)
| Usuario | Contraseña | Rol | Permisos Principales |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Administración total del sistema, reportes, usuarios y auditoría. |
| `cajero` | `admin123` | `CASHIER` | Módulo de Punto de Venta (Billing), cobranzas y devoluciones. |
| `inventario` | `admin123` | `STOCK_MONITOR`| Módulo de Catálogo de Medicamentos y Lotes de Inventario. |

### Credenciales de Grafana
- **URL**: [http://localhost:3000](http://localhost:3000)
- **Usuario**: `admin` (o el especificado en `GRAFANA_ADMIN_USER`)
- **Contraseña**: `admin123` (o el especificado en `GRAFANA_ADMIN_PASSWORD`)

---

## 14. Guía de Solución de Problemas (Troubleshooting)

### 1. El frontend muestra "No se pudo establecer conexión con el servidor"
- **Causa**: El contenedor `medizano-api-gateway` o `medizano-frontend` no está levantado.
- **Solución**: Ejecute `docker compose ps` y verifique que `medizano-api-gateway` reporte `(healthy)`. Si está reiniciando, inspeccione los logs con `docker logs medizano-api-gateway`.

### 2. Respuesta HTTP 503 Service Unavailable al consultar medicamentos o ventas
- **Causa**: El microservicio correspondiente (`catalogo-ms`, `facturacion-ms`, etc.) no ha completado su registro en Eureka Server o se cayó.
- **Solución**: Abra [http://localhost:8761](http://localhost:8761) y confirme que el microservicio figure registrado en **Instances currently registered with Eureka**. Si no está, reinícielo con `docker compose restart <nombre-microservicio>`.

### 3. Error 401 Unauthorized al consultar endpoints protegidos
- **Causa**: No se envió la cabecera `Authorization: Bearer <token>` o el token JWT expiró.
- **Solución**: Realice el inicio de sesión en `/api/auth/login` para obtener un nuevo token e inclúyalo en las solicitudes subsiguientes.

### 4. Reiniciar un microservicio individual sin detener el resto del sistema
```bash
docker compose restart catalogo-ms
```

### 5. Inspeccionar logs en vivo de un microservicio específico
```bash
docker compose logs -f usuario-ms
```

---

## 15. Documentación Interactiva OpenAPI / Swagger UI Centralizada

### 15.1. Acceso a Swagger UI
👉 **[http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)**

### 15.2. Uso del Selector "Select a definition"
> **IMPORTANTE**: En una arquitectura de microservicios distribuida, cada microservicio posee su propio ciclo de vida y contrato de API. **Cada opción del selector superior ("Select a definition") corresponde a la especificación OpenAPI independiente de un microservicio.**

Al desplegar el menú superior derecho de Swagger UI, encontrará las 7 definiciones disponibles:

| Opción en el Selector | Microservicio | Controllers Detectados | Tags de Swagger | Total Endpoints |
|---|---|---|---|:---:|
| **1. Usuarios y Autenticación** | `usuario-ms` | `AuthController`, `UserController`, `AuditLogController` | `Authentication`, `User Management`, `Audit Logs` | 10 |
| **2. Catálogo y Medicamentos** | `catalogo-ms` | `MedicineController`, `ProductoController` | `Catálogo`, `Medicamentos` | 10 |
| **3. Clientes** | `cliente-ms` | `ClienteController` | `Clientes` | 3 |
| **4. Inventario y Lotes** | `inventario-ms` | `BatchController`, `InventarioController` | `Inventario`, `Lotes e Inventario` | 14 |
| **5. Órdenes** | `orden-ms` | `OrdenController` | `Órdenes` | 4 |
| **6. Facturación y Reportes** | `facturacion-ms` | `BillingController`, `FacturacionController`, `ReportController`, `ReturnController` | `Facturación POS`, `Devoluciones POS`, `Reportes Administrativos`, `Facturación` | 18 |
| **7. Pagos** | `pago-ms` | `PagoController` | `Pagos` | 12 |

**Total del Ecosistema:** **71 endpoints** completamente documentados y testeables.

### 15.3. Autenticación con JWT Bearer en Swagger
Todas las 7 definiciones cuentan con el esquema de seguridad `bearerAuth`:
1. Seleccione en el desplegable **`1. Usuarios y Autenticación`**.
2. Despliegue `POST /api/auth/login`, presione **Try it out** y ejecute con:
   ```json
   {
     "username": "admin",
     "password": "admin123"
   }
   ```
3. Copie el token del campo `"token"`.
4. Haga clic en el botón verde **`Authorize 🔓`** en la parte superior derecha de Swagger UI y pegue el token en el campo `Value`.
5. Haga clic en **Authorize** y luego **Close**.
6. ¡Listo! Ahora puede cambiar a **Catálogo**, **Inventario**, **Clientes**, **Órdenes**, **Facturación** o **Pagos** y ejecutar cualquier endpoint protegido; el token se enviará automáticamente en la cabecera `Authorization: Bearer <token>`.

---

## 16. Catálogo Completo de Endpoints (71 Operaciones en 7 Microservicios)

A continuación se presenta el catálogo exhaustivo de los endpoints implementados en el ecosistema MediZano POS, clasificados por microservicio, con sus métodos HTTP, rutas oficiales a través del Gateway (`http://localhost:8090`), descripciones de negocio, parámetros clave y códigos de respuesta HTTP reales:

### 16.1. Microservicio de Usuarios y Autenticación (`usuario-ms`)
*Definición Swagger:* `1. Usuarios y Autenticación` | *Rutas base:* `/api/auth/**`, `/api/admin/users/**`, `/api/admin/audit/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 1 | `POST` | `/api/auth/login` | Autenticación con usuario/contraseña, valida BCrypt y emite JWT Bearer con roles | Body: `LoginRequest` (username, password) | `200 OK`, `400 Bad Request`, `401 Unauthorized` |
| 2 | `POST` | `/api/auth/logout` | Cierre de sesión y registro del evento de desconexión en auditoría | Header: `Authorization: Bearer <token>` | `200 OK`, `401 Unauthorized` |
| 3 | `GET` | `/api/admin/users` | Listar todos los usuarios del sistema con sus roles asignados | Header: `Authorization` (Rol `ADMIN`) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 4 | `GET` | `/api/admin/users/{userId}` | Obtener el perfil y permisos de un usuario por su ID | Path: `userId` (Long) | `200 OK`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 5 | `PUT` | `/api/admin/users/{userId}/password` | Cambiar y re-encriptar la contraseña de un usuario con BCrypt | Path: `userId`, Body: `ChangePasswordRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 6 | `PUT` | `/api/admin/users/{userId}/status` | Modificar el estado de activación (habilitar o bloquear acceso) | Path: `userId`, Query: `active` (Boolean) | `200 OK`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 7 | `GET` | `/api/admin/audit/all` | Consultar la bitácora completa e inmutable de eventos de seguridad | Header: `Authorization` (Rol `ADMIN`) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 8 | `GET` | `/api/admin/audit/login-logout` | Listar exclusivamente el historial de accesos y salidas de usuarios | Header: `Authorization` (Rol `ADMIN`) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 9 | `GET` | `/api/admin/audit/date-range` | Filtrar eventos de auditoría dentro de un rango de fechas ISO-8601 | Query: `startDate`, `endDate` (ISO-8601) | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` |
| 10 | `GET` | `/api/admin/audit/user/{userId}` | Obtener todas las acciones ejecutadas por un usuario específico | Path: `userId` (Long) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 11 | `DELETE` | `/api/admin/audit/all` | Depurar y purgar toda la tabla histórica de auditoría | Header: `Authorization` (Rol `ADMIN`) | `204 No Content`, `401 Unauthorized`, `403 Forbidden` |
| 12 | `DELETE` | `/api/admin/audit/login-logout` | Eliminar logs históricos de sesiones login/logout | Header: `Authorization` (Rol `ADMIN`) | `204 No Content`, `401 Unauthorized`, `403 Forbidden` |

---

### 16.2. Microservicio de Catálogo y Medicamentos (`catalogo-ms`)
*Definición Swagger:* `2. Catálogo y Medicamentos` | *Rutas base:* `/api/pharmacist/medicines/**`, `/api/v1/productos/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 13 | `POST` | `/api/pharmacist/medicines` | Registrar medicamento con especificaciones técnicas, IGV y lote inicial | Body: `CreateMedicineRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict` |
| 14 | `GET` | `/api/pharmacist/medicines/{id}` | Consultar ficha técnica y stock consolidado de medicamento por ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 15 | `GET` | `/api/pharmacist/medicines` | Listar todos los medicamentos farmacéuticos activos | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 16 | `GET` | `/api/pharmacist/medicines/search` | Búsqueda por coincidencia parcial de nombre comercial o genérico | Query: `name` (String) | `200 OK`, `401 Unauthorized` |
| 17 | `GET` | `/api/pharmacist/medicines/barcode/{barcode}` | Localización de medicamento por escaneo de código de barras (EAN-13) | Path: `barcode` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 18 | `GET` | `/api/pharmacist/medicines/barcode/search` | Búsqueda predictiva/autocompletado por prefijo de código de barras | Query: `prefix` (String) | `200 OK`, `401 Unauthorized` |
| 19 | `PUT` | `/api/pharmacist/medicines/{id}/status` | Actualizar estado operativo (ACTIVE, INACTIVE, DISCONTINUED) | Path: `id`, Query: `status` (String) | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 20 | `PUT` | `/api/pharmacist/medicines/{id}` | Actualizar datos comerciales, laboratorio, código HSN o precios | Path: `id`, Body: `UpdateMedicineRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 21 | `DELETE` | `/api/pharmacist/medicines/{id}` | Eliminar o desactivar medicamento del catálogo | Path: `id` (Long) | `204 No Content`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 22 | `GET` | `/api/v1/productos` | Listar catálogo general de productos (activos o todos) | Query: `todos` (Boolean, default false) | `200 OK`, `401 Unauthorized` |
| 23 | `GET` | `/api/v1/productos/{id}` | Obtener producto por ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 24 | `GET` | `/api/v1/productos/codigo/{codigo}` | Buscar producto por código de barras registrado | Path: `codigo` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 25 | `POST` | `/api/v1/productos` | Crear nuevo producto en catálogo general | Body: `ProductoRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` |
| 26 | `PUT` | `/api/v1/productos/{id}` | Actualizar atributos comerciales y precios de producto | Path: `id`, Body: `ProductoRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 27 | `PATCH` | `/api/v1/productos/{id}/estado` | Habilitar o inhabilitar producto para ventas POS | Path: `id`, Query: `activo` (Boolean) | `204 No Content`, `401 Unauthorized`, `404 Not Found` |
| 28 | `DELETE` | `/api/v1/productos/{id}` | Eliminar producto del catálogo general | Path: `id` (Long) | `204 No Content`, `401 Unauthorized`, `404 Not Found` |

---

### 16.3. Microservicio de Clientes (`cliente-ms`)
*Definición Swagger:* `3. Clientes` | *Rutas base:* `/api/v1/clientes/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 29 | `GET` | `/api/v1/clientes` | Listar clientes registrados (opción de filtrar solo activos) | Query: `todos` (Boolean, default false) | `200 OK`, `401 Unauthorized` |
| 30 | `GET` | `/api/v1/clientes/{id}` | Obtener cliente por su ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 31 | `GET` | `/api/v1/clientes/documento/{documento}` | Buscar cliente por documento tributario (DNI, RUC, CE) para caja | Path: `documento` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 32 | `POST` | `/api/v1/clientes` | Registrar nuevo cliente validando documento único y email | Body: `ClienteRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `409 Conflict` |
| 33 | `PUT` | `/api/v1/clientes/{id}` | Actualizar datos personales, dirección o teléfono de cliente | Path: `id`, Body: `ClienteRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 34 | `DELETE` | `/api/v1/clientes/{id}` | Eliminar o dar de baja a un cliente | Path: `id` (Long) | `204 No Content`, `401 Unauthorized`, `404 Not Found` |

---

### 16.4. Microservicio de Inventario y Lotes (`inventario-ms`)
*Definición Swagger:* `4. Inventario y Lotes` | *Rutas base:* `/api/pharmacist/batches/**`, `/api/v1/inventario/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 35 | `POST` | `/api/pharmacist/batches` | Registrar nuevo lote con fecha de caducidad, stock y precios | Body: `CreateBatchRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 36 | `GET` | `/api/pharmacist/batches/medicine/{medicineId}` | Listar lotes de un medicamento ordenados por vencimiento (FEFO) | Path: `medicineId` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 37 | `GET` | `/api/pharmacist/batches/expired` | Consultar lotes farmacéuticos vencidos o próximos a expirar | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 38 | `GET` | `/api/pharmacist/batches/low-stock` | Listar lotes cuyo stock esté por debajo del umbral mínimo | Query: `threshold` (Integer, default 10) | `200 OK`, `401 Unauthorized` |
| 39 | `GET` | `/api/pharmacist/batches` | Listar la totalidad de lotes farmacéuticos registrados | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 40 | `GET` | `/api/pharmacist/batches/barcode/{barcode}` | Localizar el lote correspondiente a una unidad física escaneada | Path: `barcode` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 41 | `GET` | `/api/pharmacist/batches/{id}` | Obtener detalle de lote por su ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 42 | `PUT` | `/api/pharmacist/batches/{id}` | Actualizar información del lote (precios, fecha de caducidad) | Path: `id`, Body: `UpdateBatchRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 43 | `PUT` | `/api/pharmacist/batches/{id}/stock` | Ajuste manual directo de existencias físicas del lote | Path: `id`, Body: `UpdateStockRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 44 | `DELETE` | `/api/pharmacist/batches/{id}` | Eliminar lote sin dispensaciones activas asociadas | Path: `id` (Long) | `204 No Content`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 45 | `GET` | `/api/pharmacist/batches/{id}/barcodes` | Listar códigos de barra serializados asignados al lote | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 46 | `POST` | `/api/pharmacist/batches/{id}/barcodes` | Agregar códigos de barra unitarios adicionales al lote | Path: `id`, Body: `AddBarcodesRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 47 | `DELETE` | `/api/pharmacist/batches/{id}/barcodes` | Eliminar códigos de barra individuales del lote | Path: `id`, Query: `barcodeIds` (List) | `204 No Content`, `401 Unauthorized`, `404 Not Found` |
| 48 | `GET` | `/api/v1/inventario` | Listar estado consolidado de existencias y almacén | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 49 | `GET` | `/api/v1/inventario/{productoId}` | Consultar existencias de un producto específico por su ID | Path: `productoId` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 50 | `POST` | `/api/v1/inventario/entrada` | Registrar entrada de mercancía / abastecimiento a almacén en kardex | Body: `MovimientoRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 51 | `POST` | `/api/v1/inventario/salida` | Registrar salida manual / merma / baja de mercancía en kardex | Body: `MovimientoRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 52 | `POST` | `/api/v1/inventario/descontar-venta` | Descuento atómico de stock invocado internamente tras venta pagada | Body: `DescuentoStockRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 53 | `GET` | `/api/v1/inventario/movimientos` | Consultar historial completo de transacciones en Kardex | Query: `productoId` (Long, opcional) | `200 OK`, `401 Unauthorized` |

---

### 16.5. Microservicio de Órdenes (`orden-ms`)
*Definición Swagger:* `5. Órdenes` | *Rutas base:* `/api/v1/ordenes/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 54 | `GET` | `/api/v1/ordenes` | Listar órdenes recientes registradas en el POS | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 55 | `GET` | `/api/v1/ordenes/{id}` | Obtener cabecera e ítems detallados de una orden por su ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 56 | `POST` | `/api/v1/ordenes` | Crear nueva orden de venta POS en estado PENDIENTE | Body: `CrearOrdenRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 57 | `POST` | `/api/v1/ordenes/{id}/confirmar-pago` | Confirmar pago de orden, transicionar a PAGADA y descontar stock | Path: `id`, Query: `referenciaPago` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `409 Conflict` |
| 58 | `POST` | `/api/v1/ordenes/{id}/cancelar` | Cancelar una orden pendiente y liberar reservas | Path: `id` (Long) | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |

---

### 16.6. Microservicio de Facturación y Reportes (`facturacion-ms`)
*Definición Swagger:* `6. Facturación y Reportes` | *Rutas base:* `/api/cashier/bills/**`, `/api/cashier/returns/**`, `/api/admin/reports/**`, `/api/v1/facturacion/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 59 | `POST` | `/api/cashier/bills` | Emitir comprobante POS (Boleta/Factura) con desglose de IGV (18%) | Body: `CreateBillRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` |
| 60 | `GET` | `/api/cashier/bills` | Listar todas las ventas registradas cronológicamente | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 61 | `GET` | `/api/cashier/bills/{id}` | Obtener comprobante de venta por ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 62 | `GET` | `/api/cashier/bills/number/{billNumber}` | Localizar comprobante por su número de serie/correlativo | Path: `billNumber` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 63 | `GET` | `/api/cashier/bills/{id}/pdf` | Generar y descargar el comprobante oficial en formato PDF | Path: `id` (Long) | `200 OK (application/pdf)`, `401 Unauthorized`, `404 Not Found` |
| 64 | `PUT` | `/api/cashier/bills/{id}/cancel` | Anular comprobante emitido registrando el motivo administrativo | Path: `id`, Query: `reason` (String) | `204 No Content`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 65 | `POST` | `/api/cashier/returns` | Procesar devolución de ítems y emitir nota de crédito | Body: `ReturnRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| 65b | `GET` | `/api/cashier/returns` | Listar todas las devoluciones emitidas (historial completo para módulo Devoluciones) | Header: `Authorization` (Rol `CASHIER` o `ADMIN`) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 66 | `GET` | `/api/cashier/returns/{id}` | Obtener nota de devolución por su ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 67 | `GET` | `/api/cashier/returns/bill/{billId}` | Listar devoluciones vinculadas a una factura específica | Path: `billId` (Long) | `200 OK`, `401 Unauthorized` |
| 68 | `GET` | `/api/admin/reports/sales` | Reporte consolidado de ventas, tickets y montos por fecha | Query: `startDate`, `endDate` (ISO) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 69 | `GET` | `/api/admin/reports/cash-register` | Arqueo y balance de caja por turno y medio de pago | Query: `startDate`, `endDate` (ISO) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 70 | `GET` | `/api/admin/reports/gst` | Reporte tributario de base imponible e impuesto IGV (18%) | Query: `startDate`, `endDate` (ISO) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 71 | `GET` | `/api/admin/reports/stock` | Reporte de valorización monetaria total de inventario y rotación | Header: `Authorization` (Rol `ADMIN`) | `200 OK`, `401 Unauthorized`, `403 Forbidden` |
| 72 | `GET` | `/api/v1/facturacion` | Listar facturas del módulo transaccional v1 | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 73 | `GET` | `/api/v1/facturacion/{id}` | Obtener factura transaccional v1 por ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 74 | `GET` | `/api/v1/facturacion/orden/{ordenId}` | Obtener comprobante emitido para una orden POS | Path: `ordenId` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 75 | `POST` | `/api/v1/facturacion/generar` | Generar comprobante electrónico transaccional para orden pagada | Body: `GenerarFacturaRequest` | `201 Created`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| 76 | `GET` | `/api/v1/facturacion/{id}/pdf` | Descargar PDF de comprobante por ID de factura | Path: `id` (Long) | `200 OK (application/pdf)`, `401 Unauthorized`, `404 Not Found` |
| 77 | `GET` | `/api/v1/facturacion/orden/{ordenId}/pdf` | Descargar PDF de comprobante por ID de orden | Path: `ordenId` (Long) | `200 OK (application/pdf)`, `401 Unauthorized`, `404 Not Found` |

---

### 16.7. Microservicio de Pagos (`pago-ms`)
*Definición Swagger:* `7. Pagos` | *Rutas base:* `/api/v1/pagos/**`

| # | Método | Ruta (Gateway) | Descripción de Negocio | Parámetros / Body | Códigos HTTP |
|:---:|:---:|---|---|---|---|
| 78 | `GET` | `/api/v1/pagos/config` | Configuración pública unificada (PayPal Client ID y MP Public Key) | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 79 | `GET` | `/api/v1/pagos/paypal/config` | Obtener Client ID y moneda configurada para PayPal Sandbox | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 80 | `GET` | `/api/v1/pagos/mercadopago/config` | Obtener Public Key y moneda de Mercado Pago Checkout Pro | Header: `Authorization` | `200 OK`, `401 Unauthorized` |
| 81 | `GET` | `/api/v1/pagos/orden/{ordenId}` | Listar historial de transacciones de pago asociadas a una orden | Path: `ordenId` (Long) | `200 OK`, `401 Unauthorized` |
| 82 | `GET` | `/api/v1/pagos/{id}` | Obtener detalle de una transacción de pago por su ID | Path: `id` (Long) | `200 OK`, `401 Unauthorized`, `404 Not Found` |
| 83 | `POST` | `/api/v1/pagos/paypal/create-order` | Crear orden en PayPal Orders v2 Sandbox con links de aprobación | Body: `PayPalOrderRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `502 Bad Gateway` |
| 84 | `POST` | `/api/v1/pagos/paypal/order` | Endpoint complementario para creación de orden en PayPal | Body: `PayPalOrderRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `502 Bad Gateway` |
| 85 | `POST` | `/api/v1/pagos/paypal/capture/{paypalOrderId}` | Capturar fondos de orden aprobada en PayPal Sandbox | Path: `paypalOrderId` (String) | `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `502 Bad Gateway` |
| 86 | `GET` | `/api/v1/pagos/paypal/order/{paypalOrderId}` | Consultar estado en tiempo real contra la API de PayPal Sandbox | Path: `paypalOrderId` (String) | `200 OK`, `401 Unauthorized`, `404 Not Found`, `502 Bad Gateway` |
| 87 | `POST` | `/api/v1/pagos/mercadopago/preference` | Crear preferencia en Mercado Pago Checkout Pro (retorna init_point) | Body: `MercadoPagoPreferenceRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized` |
| 88 | `POST` | `/api/v1/pagos/mercadopago/verify` | Verificar y confirmar pago acreditado consultando API de Mercado Pago | Body: `MercadoPagoVerifyRequest` | `200 OK`, `400 Bad Request`, `401 Unauthorized` |
| 89 | `POST` | `/api/v1/pagos/mercadopago/webhook` | Webhook IPN asíncrono con validación de firma criptográfica HMAC | Headers: `x-signature`, `x-request-id` | `200 OK`, `401 Unauthorized`, `500 Internal Error` |

---

### Resumen de Cobertura de la API

- **Microservicios activos:** 7 microservicios de negocio + 1 API Gateway + 1 Eureka Server.
- **Definiciones OpenAPI centralizadas:** 7 definiciones independientes integradas en Swagger UI.
- **Rutas y operaciones cubiertas:** 89 rutas mapeadas cubriendo los 71 endpoints funcionales del sistema.
- **Seguridad perimetral:** Esquema unificado `bearerAuth` (JWT) con roles `ADMIN`, `CASHIER`, `STOCK_MONITOR`, `PHARMACIST`.
- **Acceso interactivo:** [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html) a través del API Gateway.

---

## 17. Módulo de Devoluciones POS y Reembolsos (Frontend & Backend)

### 17.1. Diagnóstico del Error HTTP 405 Method Not Allowed
Al ingresar al frontend Angular en `http://localhost:4200` y acceder a la ruta **/returns** (Dashboard → Devoluciones), el componente `ReturnsComponent` ejecutaba en su ciclo `ngOnInit()`:
```typescript
ngOnInit(): void {
  this.loadReturnHistory();
}

loadReturnHistory(): void {
  this.returnService.getAllReturns().subscribe(...);
}
```
Esto emitía una solicitud HTTP `GET` hacia `/api/cashier/returns` (a través del reverse proxy de Nginx hacia el API Gateway).

**Causa Raíz:**
En el microservicio `facturacion-ms`, el controlador `ReturnController.java` (`@RequestMapping("/api/cashier/returns")`) únicamente exponía:
- `POST /api/cashier/returns`: Procesamiento de devolución.
- `GET /api/cashier/returns/{id}`: Consulta por ID.
- `GET /api/cashier/returns/bill/{billId}`: Devoluciones por factura.

No existía la anotación `@GetMapping` en la raíz `/api/cashier/returns`. Por ello, Spring MVC rechazaba la petición con `HTTP 405 Method Not Allowed`.

**Solución Implementada:**
1. **Backend (`facturacion-ms`)**: Se incorporó el endpoint `@GetMapping public ResponseEntity<List<ReturnResponse>> getAllReturns()` delegando en `returnService.getAllReturns()`, el cual ya se encontraba implementado consultando las devoluciones y sus ítems en la base de datos PostgreSQL `medizano_facturacion`.
2. **Frontend (`api.service.ts`)**: Se incorporó el mapeo para el código HTTP `405` y la traducción contextual de la cadena `method not allowed` para presentar un mensaje amigable al usuario en español: `"La operación solicitada no está permitida para este recurso (Method Not Allowed)."`.
3. **Swagger UI**: Se actualizó la definición `6. Facturación y Reportes` incorporando `@Operation` y `@ApiResponses` (200, 401, 403) para `getAllReturns`.

### 17.2. Flujo Funcional de Devoluciones
El ciclo operativo de devoluciones en el POS se compone de:
1. **Búsqueda de Comprobante**: El cajero ingresa el número de comprobante (ej.: `FAC-20260904-5475`). El sistema valida que la venta exista, no esté anulada y se encuentre en estado `PAID`.
2. **Selección de Productos y Cantidades**: Se listan los ítems asociados a la venta con su lote y precio unitario. El usuario selecciona qué ítems desea devolver y especifica la cantidad a retornar (con validación de que la cantidad a devolver no supere la cantidad vendida).
3. **Cálculo de Reembolso**: El sistema calcula en tiempo real el monto de devolución por línea (`precioUnitario * cantidadDevuelta`) y el total a reintegrar en soles (S/).
4. **Registro de Nota de Devolución**: Al confirmar con motivo obligatorio, se envía `POST /api/cashier/returns`. El backend genera un identificador correlativo único `DEV-yyyyMMdd-XXXX` y almacena los ítems devueltos en la tabla `returns` y `return_items`.
5. **Actualización de Estado de Venta**: Si el importe total devuelto cubre la totalidad de la venta original, el estado de pago del comprobante transiciona automáticamente a `REFUNDED`. En devoluciones menores se mantiene en tipo `PARTIAL`.
6. **Historial y Auditoría**: El módulo permite alternar la vista de historial (`Ver historial`) con estadísticas en vivo de cantidad de devoluciones emitidas y monto total devuelto acumulado.

### 17.3. Integración con Inventario (Regla del Sistema)
- **Dominio y Responsabilidad**: `facturacion-ms` es responsable del dominio tributario, fiscal, notas de crédito y estados de cobro/reembolso.
- **Lógica de Stock**: La salida atómica de stock se efectúa al momento de pagar una orden en `orden-ms` mediante comunicación Feign hacia `inventario-ms` (`POST /api/v1/inventario/descontar-venta`).
- **Comportamiento en Devoluciones**: En la arquitectura actual de microservicios, el procesamiento de notas de devolución en `facturacion-ms` genera el registro contable y el crédito a favor del cliente sin alterar automáticamente los lotes de almacén de `inventario-ms`, evitando inconsistencias por productos abiertos, dañados o que requieren reinspección farmacéutica manual antes de reincorporarse a la venta al público.


