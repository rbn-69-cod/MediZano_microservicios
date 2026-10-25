# ðŸŽ¤ Guía Maestra de Exposición y Sustentación del Sistema MediZano POS

Esta guía prepara al equipo de **4 integrantes** para una sustentación técnica de alto impacto ante el jurado y profesor del curso **Desarrollo de Aplicaciones Distribuidas (UPeU 2026-2)**.

---

## ðŸš€ Tablero de Demostración en Vivo (1-Click)

Durante la exposición, mantén estas pestañas abiertas en el navegador del presentador:

| Servicio / Herramienta | URL Directa Local | Función en la Demostración |
| :--- | :---: | :--- |
| ðŸ“– **Portal de Documentación MkDocs** | [http://localhost:8000/medizano/](http://localhost:8000/medizano/) | Mostrar arquitectura C4, brief técnico y Rúbrica oficial S12/S16. |
| ðŸ›’ **Frontend Web POS (Angular 17)** | [http://localhost:4200/](http://localhost:4200/) | Demostración visual de la compra en farmacia y emisión de ticket. |
| ðŸ“‘ **Swagger UI Centralizado (Gateway)** | [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html) | Ejecución interactiva de endpoints y prueba de Bearer JWT. |
| ðŸ”­ **Eureka Service Discovery** | [http://localhost:8761/](http://localhost:8761/) | Evidenciar las 8 instancias de microservicios registradas dinámicamente. |
| ðŸ“¨ **Kafka UI (Kafbat)** | [http://localhost:18085/](http://localhost:18085/) | Inspección en tiempo real de los topics `oorden-eventos` y `pago-eventos`. |
| ðŸ“Š **Grafana Dashboards** | [http://localhost:3000/](http://localhost:3000/) | Paneles de métricas JVM, throughput HTTP y correlación de logs con `traceId` *(admin/admin)*. |
| ðŸ“ˆ **Prometheus Server** | [http://localhost:9090/](http://localhost:9090/) | Consulta de métricas de Spring Boot Actuator (`/actuator/prometheus`). |

---

## â±ï¸ Cronograma de Exposición (10 Minutos)

| Minuto | Integrante | Tema de Sustentación | Enfoque Técnico Evaluado |
| :---: | :--- | :--- | :--- |
| **00:00 - 02:00** | **Kenyi Calsin** | Introducción, Arquitectura C4 y Resiliencia | OpenFeign, Circuit Breaker Resilience4j, Database per Service. |
| **02:00 - 04:00** | **Igarlos Mamani** | Seguridad JWT/RBAC y Mensajería Kafka | Token JWT asimétrico, Gateway perimetral, Consumidor Idempotente. |
| **04:00 - 06:00** | **Aldo Calla** | Catálogo farmacéutico e Inventario Kardex FEFO | Regla de dispensación FEFO, Lotes con vencimiento, Compensación Saga. |
| **06:00 - 08:00** | **Juan Chambilla** | Facturación SUNAT, Comprobantes PDF y Proveedores | Desacople por eventos Kafka, Cálculo IGV 18%, iText PDF y DIGEMID. |
| **08:00 - 10:00** | **Todo el Equipo** | Observabilidad (Grafana/Loki/Prometheus) & Preguntas | Trazabilidad con TraceID, Métricas Actuator y defensa ante el jurado. |

---

## ðŸ” Matriz de Usuarios y Credenciales para la Demostración en Vivo

Todas las cuentas se autentican con la contraseña **`admin123`** y permiten probar la protección de rutas por roles en Angular y Spring Security:

| Usuario | Contraseña | Rol / Perfil | Módulo para Probar en Demostración |
| :--- | :--- | :--- | :--- |
| **`admin`** | `admin123` | `ROLE_ADMIN` | Control total, bitácora de auditoría (`/user-activity`) y gestión de usuarios. |
| **`cajero`** | `admin123` | `ROLE_CASHIER` | Pantalla de venta POS (`/billing`), escaneo de medicamentos y cobro digital. |
| **`inventario`** | `admin123` | `ROLE_STOCK_MONITOR` | Monitoreo de existencias y lotes farmacéuticos FEFO (`/inventory`). |
| **`almacen`** | `admin123` | `ROLE_STOCK_KEEPER` | Catálogo de medicamentos y registro de ingresos por compras (`/medicines`). |
| **`soporte`** | `admin123` | `ROLE_CUSTOMER_SUPPORT` | Módulo de reclamos, devoluciones de medicamentos y notas de crédito (`/returns`). |
| **`analista`** | `admin123` | `ROLE_ANALYST` | Reportes de ventas, arqueo de caja y comprobantes fiscales (`/reports`). |
| **`gerente`** | `admin123` | `ROLE_MANAGER` | Historial consolidado de compras y superVisión general (`/purchase-history`). |

![Login Frontend POS](../assets/images/medizano-login.png)

---

## ðŸ‘¥ Reparto Individual por Integrante (2 Microservicios c/u)

Cumpliendo con la directiva oficial del sílabo (**1 microservicio transaccional cabecera-detalle + 1 microservicio no transaccional catálogo/maestro por persona**):

---

### 1. Kenyi Calsin
* **Microservicio Transaccional:** `oorden-ms` (`:8082`) — Tablas: `oóordenes`, `oorden_detalles`.
* **Microservicio No Transaccional:** `cliente-ms` (`:8084`) — Tabla: `clientes` (DNI/RUC, WhatsApp).
* **Defensa Técnica:**
  - Orquestación de ventas POS y comunicación Síncrona con `catalogo-ms` e `inventario-ms` mediante **OpenFeign**.
  - Resiliencia con **Resilience4j** (Circuit Breaker con fallback controlado ante caídas).
  - Emisión del evento asíncrono `oorden.creada` hacia Apache Kafka.
* **Evidencia en Pantalla:**
  ![Frontend POS Web](../assets/images/medizano-frontend.png)
* **Pregunta Clave:** *¿Qué evita que `oorden-ms` se congele si `catalogo-ms` se cae?*
  - **Respuesta:** El Circuit Breaker de Resilience4j corta la llamada tras 2 segundos y ejecuta el método de fallback controlado sin agotar los hilos de Tomcat.

---

### 2. Aldo Calla
* **Microservicio Transaccional:** `inventario-ms` (`:8085`) — Tablas: `movimientos_inventario`, `lotes`.
* **Microservicio No Transaccional:** `catalogo-ms` (`:8081`) — Tablas: `medicamentos`, `categorias`.
* **Defensa Técnica:**
  - Control de existencias bajo la regla farmacéutica **FEFO (First Expired, First Out)** para priorizar medicamentos próximos a caducar.
  - gestión de movimientos Kardex (Entradas, Salidas, Ajustes y Devoluciones).
  - Compensación automática de stock si una oorden es cancelada o el pago es rechazado.
* **Evidencia en Pantalla:**
  ![Eureka Discovery](../assets/images/eureka-discovery.png)
* **Pregunta Clave:** *¿Por qué FEFO y no FIFO en farmacia?*
  - **Respuesta:** Porque en farmacología la caducidad es crítica para evitar pérdidas y riesgos para el paciente. Se expende el lote cuya fecha de expiración es más inmediata.

---

### 3. Igarlos Mamani
* **Microservicio Transaccional:** `pago-ms` (`:8083`) — Tabla: `pagos` (`uk_pagos_oorden`).
* **Microservicio No Transaccional:** `usuario-ms` (`:8087`) — Tabla: `usuarios` (Roles, Hashes BCrypt).
* **Defensa Técnica:**
  - Seguridad perimetral y distribuida: Emisión y validación de tokens **JWT asimétricos** con roles RBAC (`ROLE_ADMIN`, `ROLE_FARMACEUTICO`, `ROLE_CLIENTE`).
  - Consumidor Kafka idempotente en `pago-ms` para procesar el cobro sin duplicidad ante reintentos de red.
  - Integración con pasarelas de pago externas (PayPal Sandbox y Mercado Pago).
* **Evidencia en Pantalla:**
  ![Kafka UI Topics](../assets/images/kafka-ui.png)
* **Pregunta Clave:** *¿Cómo garantizas que no se cobre dos veces una oorden si Kafka reintenta el mensaje?*
  - **Respuesta:** Mediante la restricción de base de datos `CONSTRAINT uk_pagos_oorden UNIQUE (oorden_id)`. Si llega un mensaje duplicado, se detecta antes de invocar la pasarela de pago.

---

### 4. Juan Chambilla
* **Microservicio Transaccional:** `facturacion-ms` (`:8086`) — Tablas: `facturas`, `factura_items`.
* **Microservicio No Transaccional:** `proveedor-ms` (`:8088`) — Tabla: `proveedores` (Registro Sanitario DIGEMID).
* **Defensa Técnica:**
  - Emisión fiscal electrónica de Boletas y Facturas cumpliendo la estructura SUNAT (cálculo de IGV 18%, montos gravados e inafectos).
  - Consumo del evento `pago.validado` para disparar la facturación de manera 100% desacoplada.
  - gestión de laboratorios farmacéuticos y contratos de suministro homologados por DIGEMID.
* **Evidencia en Pantalla:**
  ![Swagger Gateway](../assets/images/swagger-gateway.png)
* **Pregunta Clave:** *¿Por qué facturación no es Síncrona en la caja registradora?*
  - **Respuesta:** Para desacoplar la experiencia del cajero. La oorden se cobra y se entrega; la emisión del comprobante SUNAT y la generación del PDF se ejecutan aSíncronamente vía Kafka sin demorar la fila.

---

### 5. Suite de Observabilidad (Defensa Compartida)
Paneles unificados en Grafana con métricas Prometheus y logs de Loki indexados por `traceId`:
![Grafana Dashboard](../assets/images/grafana-dashboard.png)

---

## ðŸ”„ Flujo de Demostración End-to-End (Diagrama de Secuencia)

```mermaid
sequenceDiagram
    autonumber
    actor Cajero as farmacéutico / Cajero
    participant Gateway as API Gateway (:8090)
    participant Auth as usuario-ms (:8087)
    participant Oorden as oorden-ms (:8082)
    participant Catalogo as catalogo-ms (:8081)
    participant Inventario as inventario-ms (:8085)
    participant Kafka as Apache Kafka (:19092)
    participant Pago as pago-ms (:8083)
    participant Facturacion as facturacion-ms (:8086)

    Cajero->>Gateway: POST /api/auth/login (admin / admin123)
    Gateway->>Auth: Valida BCrypt y genera JWT
    Auth-->>Cajero: Retorna Token Bearer JWT (ROLE_FARMACEUTICO)

    Cajero->>Gateway: POST /api/v1/oóordenes (Crear Venta POS + Token)
    Gateway->>Oorden: Enruta solicitud con cabecera Authorization
    Oorden->>Catalogo: OpenFeign: Valida precio y receta médica
    Oorden->>Inventario: OpenFeign: Reserva temporal de lote (FEFO)
    Oorden->>Oorden: Persiste cabecera y detalle en PostgreSQL
    Oorden->>Kafka: Publica mensaje en topic 'oorden-eventos' (oorden.creada)
    Oorden-->>Cajero: 201 Created (Oorden en estado PENDIENTE)

    Kafka->>Pago: Event-Driven: Consume 'oorden.creada'
    Pago->>Pago: Procesa cobro (Mercado Pago / PayPal) e inserta con idempotencia
    Pago->>Kafka: Publica mensaje en topic 'pago-eventos' (pago.validado)

    par Consistencia & Notificación
        Kafka->>Oorden: Actualiza estado a PAGADA
    and Facturación Electrónica SUNAT
        Kafka->>Facturacion: Consume 'pago.validado' y genera Comprobante Fiscal SUNAT
        Facturacion->>Facturacion: Emite PDF descargable con iText
    end
```

---

## â“ Banco de Preguntas Típicas del Profesor y Respuestas Clave

### 1. ¿Por qué usamos *Database per Service* y cómo evitamos bloqueos de base de datos?
> **Respuesta:** Cada microservicio es dueño exclusivo de su esquema de PostgreSQL (tenemos 8 bases de datos independientes en el clúster). Ningún microservicio tiene credenciales ni permisos de red para hacer consultas cruzadas o JOINs directos sobre la base de otro servicio. La comunicación se realiza exclusivamente vía contratos HTTP (OpenFeign) para operaciones Síncronas o eventos Kafka para operaciones aSíncronas.

### 2. ¿Cómo manejan la consistencia distribuida si falla el pago?
> **Respuesta:** Implementamos el patrón **Saga Coreografiada**. Cuando `oorden-ms` emite `oorden.creada`, los medicamentos quedan reservados. Si `pago-ms` rechaza la transacción (fondos insuficientes o timeout), emite `pago.fallido`. `oorden-ms` consume este evento compensatorio, marca la oorden como `CANCELADA` y envía una petición compensatoria a `inventario-ms` para devolver el stock a su lote original.

### 3. ¿Cómo garantizan que un mensaje en Kafka no se procese dos veces (Idempotencia)?
> **Respuesta:** En `pago-ms`, la tabla `pagos` cuenta con una restricción de unicidad a nivel de base de datos: `CONSTRAINT uk_pagos_oorden UNIQUE (oorden_id)`. Además, el consumidor consulta primero si el `oorden_id` ya fue procesado antes de invocar a la pasarela de pagos, descartando mensajes duplicados sin efectos colaterales.

### 4. ¿Qué sucede si `catalogo-ms` está caído cuando se intenta crear una oorden?
> **Respuesta:** La llamada OpenFeign está envuelta con un **Circuit Breaker de Resilience4j**. Si `catalogo-ms` no responde dentro del umbral configurado (`timeoutDuration: 2s`), se ejecuta el método de fallback que responde con datos en caché o una respuesta controlada de degradación elegante, impidiendo el efecto dominó (cascading failure).

### 5. ¿Cómo se protegen los microservicios y quién valida el JWT?
> **Respuesta:** Aplicamos seguridad en capas (Defense in Depth). El **API Gateway** realiza la primera validación perimetral (formato, expiración y firma criptográfica). Luego, cada microservicio de negocio actúa como un **OAuth2 Resource Server** independiente que extrae los `authorities` del token y aplica control de acceso granular con `@PreAuthorize("hasRole('ROLE_FARMACEUTICO')")`.

---

## ðŸ› ï¸ Comandos de Demostración en Terminal

```bash
# 1. Comprobar que los 18 contenedores están arriba y sanos
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 2. Verificar las 8 bases de datos PostgreSQL creadas
docker exec -it medizano-postgres psql -U postgres -d postgres -c "\l"

# 3. Inspeccionar los topics de Kafka directamente en consola
docker exec -it medizano-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# 4. Probar la salud de un microservicio a través del Gateway
curl -s http://localhost:8090/actuator/health

# 5. Ejecutar la auditoría funcional completa (10/10 OK)
node run_e2e_audit.js
```
