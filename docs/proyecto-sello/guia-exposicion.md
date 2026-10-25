# ðŸŽ¤ GuÃ­a Maestra de ExposiciÃ³n y SustentaciÃ³n del Sistema MediZano POS

Esta guÃ­a prepara al equipo de **4 integrantes** para una sustentaciÃ³n tÃ©cnica de alto impacto ante el jurado y profesor del curso **Desarrollo de Aplicaciones Distribuidas (UPeU 2026-2)**.

---

## ðŸš€ Tablero de DemostraciÃ³n en Vivo (1-Click)

Durante la exposiciÃ³n, mantÃ©n estas pestaÃ±as abiertas en el navegador del presentador:

| Servicio / Herramienta | URL Directa Local | FunciÃ³n en la DemostraciÃ³n |
| :--- | :---: | :--- |
| ðŸ“– **Portal de DocumentaciÃ³n MkDocs** | [http://localhost:8000/medizano/](http://localhost:8000/medizano/) | Mostrar arquitectura C4, brief tÃ©cnico y Rúbrica oficial S12/S16. |
| ðŸ›’ **Frontend Web POS (Angular 17)** | [http://localhost:4200/](http://localhost:4200/) | DemostraciÃ³n visual de la compra en farmacia y emisiÃ³n de ticket. |
| ðŸ“‘ **Swagger UI Centralizado (Gateway)** | [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html) | EjecuciÃ³n interactiva de endpoints y prueba de Bearer JWT. |
| ðŸ”­ **Eureka Service Discovery** | [http://localhost:8761/](http://localhost:8761/) | Evidenciar las 8 instancias de microservicios registradas dinÃ¡micamente. |
| ðŸ“¨ **Kafka UI (Kafbat)** | [http://localhost:18085/](http://localhost:18085/) | InspecciÃ³n en tiempo real de los topics `orden-eventos` y `pago-eventos`. |
| ðŸ“Š **Grafana Dashboards** | [http://localhost:3000/](http://localhost:3000/) | Paneles de mÃ©tricas JVM, throughput HTTP y correlaciÃ³n de logs con `traceId` *(admin/admin)*. |
| ðŸ“ˆ **Prometheus Server** | [http://localhost:9090/](http://localhost:9090/) | Consulta de mÃ©tricas de Spring Boot Actuator (`/actuator/prometheus`). |

---

## â±ï¸ Cronograma de ExposiciÃ³n (10 Minutos)

| Minuto | Integrante | Tema de SustentaciÃ³n | Enfoque TÃ©cnico Evaluado |
| :---: | :--- | :--- | :--- |
| **00:00 - 02:00** | **Kenyi Calsin** | IntroducciÃ³n, Arquitectura C4 y Resiliencia | OpenFeign, Circuit Breaker Resilience4j, Database per Service. |
| **02:00 - 04:00** | **Igarlos Mamani** | Seguridad JWT/RBAC y MensajerÃ­a Kafka | Token JWT asimÃ©trico, Gateway perimetral, Consumidor Idempotente. |
| **04:00 - 06:00** | **Aldo Calla** | CatÃ¡logo farmacéutico e Inventario Kardex FEFO | Regla de dispensaciÃ³n FEFO, Lotes con vencimiento, CompensaciÃ³n Saga. |
| **06:00 - 08:00** | **Juan Chambilla** | FacturaciÃ³n SUNAT, Comprobantes PDF y Proveedores | Desacople por eventos Kafka, CÃ¡lculo IGV 18%, iText PDF y DIGEMID. |
| **08:00 - 10:00** | **Todo el Equipo** | Observabilidad (Grafana/Loki/Prometheus) & Preguntas | Trazabilidad con TraceID, MÃ©tricas Actuator y defensa ante el jurado. |

---

## ðŸ” Matriz de Usuarios y Credenciales para la DemostraciÃ³n en Vivo

Todas las cuentas se autentican con la contraseÃ±a **`admin123`** y permiten probar la protecciÃ³n de rutas por roles en Angular y Spring Security:

| Usuario | ContraseÃ±a | Rol / Perfil | MÃ³dulo para Probar en DemostraciÃ³n |
| :--- | :--- | :--- | :--- |
| **`admin`** | `admin123` | `ROLE_ADMIN` | Control total, bitÃ¡cora de auditorÃ­a (`/user-activity`) y gestión de usuarios. |
| **`cajero`** | `admin123` | `ROLE_CASHIER` | Pantalla de venta POS (`/billing`), escaneo de medicamentos y cobro digital. |
| **`inventario`** | `admin123` | `ROLE_STOCK_MONITOR` | Monitoreo de existencias y lotes farmacéuticos FEFO (`/inventory`). |
| **`almacen`** | `admin123` | `ROLE_STOCK_KEEPER` | CatÃ¡logo de medicamentos y registro de ingresos por compras (`/medicines`). |
| **`soporte`** | `admin123` | `ROLE_CUSTOMER_SUPPORT` | MÃ³dulo de reclamos, devoluciones de medicamentos y notas de crÃ©dito (`/returns`). |
| **`analista`** | `admin123` | `ROLE_ANALYST` | Reportes de ventas, arqueo de caja y comprobantes fiscales (`/reports`). |
| **`gerente`** | `admin123` | `ROLE_MANAGER` | Historial consolidado de compras y superVisión general (`/purchase-history`). |

![Login Frontend POS](../assets/images/medizano-login.png)

---

## ðŸ‘¥ Reparto Individual por Integrante (2 Microservicios c/u)

Cumpliendo con la directiva oficial del sÃ­labo (**1 microservicio transaccional cabecera-detalle + 1 microservicio no transaccional catÃ¡logo/maestro por persona**):

---

### 1. Kenyi Calsin
* **Microservicio Transaccional:** `orden-ms` (`:8082`) â€” Tablas: `ordenes`, `orden_detalles`.
* **Microservicio No Transaccional:** `cliente-ms` (`:8084`) â€” Tabla: `clientes` (DNI/RUC, WhatsApp).
* **Defensa TÃ©cnica:**
  - OrquestaciÃ³n de ventas POS y comunicaciÃ³n Síncrona con `catalogo-ms` e `inventario-ms` mediante **OpenFeign**.
  - Resiliencia con **Resilience4j** (Circuit Breaker con fallback controlado ante caÃ­das).
  - EmisiÃ³n del evento asÃ­ncrono `orden.creada` hacia Apache Kafka.
* **Evidencia en Pantalla:**
  ![Frontend POS Web](../assets/images/medizano-frontend.png)
* **Pregunta Clave:** *Â¿QuÃ© evita que `orden-ms` se congele si `catalogo-ms` se cae?*
  - **Respuesta:** El Circuit Breaker de Resilience4j corta la llamada tras 2 segundos y ejecuta el mÃ©todo de fallback controlado sin agotar los hilos de Tomcat.

---

### 2. Aldo Calla
* **Microservicio Transaccional:** `inventario-ms` (`:8085`) â€” Tablas: `movimientos_inventario`, `lotes`.
* **Microservicio No Transaccional:** `catalogo-ms` (`:8081`) â€” Tablas: `medicamentos`, `categorias`.
* **Defensa TÃ©cnica:**
  - Control de existencias bajo la regla farmacÃ©utica **FEFO (First Expired, First Out)** para priorizar medicamentos prÃ³ximos a caducar.
  - gestión de movimientos Kardex (Entradas, Salidas, Ajustes y Devoluciones).
  - CompensaciÃ³n automÃ¡tica de stock si una orden es cancelada o el pago es rechazado.
* **Evidencia en Pantalla:**
  ![Eureka Discovery](../assets/images/eureka-discovery.png)
* **Pregunta Clave:** *Â¿Por quÃ© FEFO y no FIFO en farmacia?*
  - **Respuesta:** Porque en farmacologÃ­a la caducidad es crÃ­tica para evitar pÃ©rdidas y riesgos para el paciente. Se expende el lote cuya fecha de expiraciÃ³n es mÃ¡s inmediata.

---

### 3. Igarlos Mamani
* **Microservicio Transaccional:** `pago-ms` (`:8083`) â€” Tabla: `pagos` (`uk_pagos_orden`).
* **Microservicio No Transaccional:** `usuario-ms` (`:8087`) â€” Tabla: `usuarios` (Roles, Hashes BCrypt).
* **Defensa TÃ©cnica:**
  - Seguridad perimetral y distribuida: EmisiÃ³n y validaciÃ³n de tokens **JWT asimÃ©tricos** con roles RBAC (`ROLE_ADMIN`, `ROLE_FARMACEUTICO`, `ROLE_CLIENTE`).
  - Consumidor Kafka idempotente en `pago-ms` para procesar el cobro sin duplicidad ante reintentos de red.
  - IntegraciÃ³n con pasarelas de pago externas (PayPal Sandbox y Mercado Pago).
* **Evidencia en Pantalla:**
  ![Kafka UI Topics](../assets/images/kafka-ui.png)
* **Pregunta Clave:** *Â¿CÃ³mo garantizas que no se cobre dos veces una orden si Kafka reintenta el mensaje?*
  - **Respuesta:** Mediante la restricciÃ³n de base de datos `CONSTRAINT uk_pagos_orden UNIQUE (orden_id)`. Si llega un mensaje duplicado, se detecta antes de invocar la pasarela de pago.

---

### 4. Juan Chambilla
* **Microservicio Transaccional:** `facturacion-ms` (`:8086`) â€” Tablas: `facturas`, `factura_items`.
* **Microservicio No Transaccional:** `proveedor-ms` (`:8088`) â€” Tabla: `proveedores` (Registro Sanitario DIGEMID).
* **Defensa TÃ©cnica:**
  - EmisiÃ³n fiscal electrÃ³nica de Boletas y Facturas cumpliendo la estructura SUNAT (cÃ¡lculo de IGV 18%, montos gravados e inafectos).
  - Consumo del evento `pago.validado` para disparar la facturaciÃ³n de manera 100% desacoplada.
  - gestión de laboratorios farmacéuticos y contratos de suministro homologados por DIGEMID.
* **Evidencia en Pantalla:**
  ![Swagger Gateway](../assets/images/swagger-gateway.png)
* **Pregunta Clave:** *Â¿Por quÃ© facturaciÃ³n no es Síncrona en la caja registradora?*
  - **Respuesta:** Para desacoplar la experiencia del cajero. La orden se cobra y se entrega; la emisiÃ³n del comprobante SUNAT y la generaciÃ³n del PDF se ejecutan aSíncronamente vÃ­a Kafka sin demorar la fila.

---

### 5. Suite de Observabilidad (Defensa Compartida)
Paneles unificados en Grafana con mÃ©tricas Prometheus y logs de Loki indexados por `traceId`:
![Grafana Dashboard](../assets/images/grafana-dashboard.png)

---

## ðŸ”„ Flujo de DemostraciÃ³n End-to-End (Diagrama de Secuencia)

```mermaid
sequenceDiagram
    autonumber
    actor Cajero as farmacéutico / Cajero
    participant Gateway as API Gateway (:8090)
    participant Auth as usuario-ms (:8087)
    participant Orden as orden-ms (:8082)
    participant Catalogo as catalogo-ms (:8081)
    participant Inventario as inventario-ms (:8085)
    participant Kafka as Apache Kafka (:19092)
    participant Pago as pago-ms (:8083)
    participant Facturacion as facturacion-ms (:8086)

    Cajero->>Gateway: POST /api/auth/login (admin / admin123)
    Gateway->>Auth: Valida BCrypt y genera JWT
    Auth-->>Cajero: Retorna Token Bearer JWT (ROLE_FARMACEUTICO)

    Cajero->>Gateway: POST /api/v1/ordenes (Crear Venta POS + Token)
    Gateway->>Orden: Enruta solicitud con cabecera Authorization
    Orden->>Catalogo: OpenFeign: Valida precio y receta mÃ©dica
    Orden->>Inventario: OpenFeign: Reserva temporal de lote (FEFO)
    Orden->>Orden: Persiste cabecera y detalle en PostgreSQL
    Orden->>Kafka: Publica mensaje en topic 'orden-eventos' (orden.creada)
    Orden-->>Cajero: 201 Created (Orden en estado PENDIENTE)

    Kafka->>Pago: Event-Driven: Consume 'orden.creada'
    Pago->>Pago: Procesa cobro (Mercado Pago / PayPal) e inserta con idempotencia
    Pago->>Kafka: Publica mensaje en topic 'pago-eventos' (pago.validado)

    par Consistencia & NotificaciÃ³n
        Kafka->>Orden: Actualiza estado a PAGADA
    and FacturaciÃ³n ElectrÃ³nica SUNAT
        Kafka->>Facturacion: Consume 'pago.validado' y genera Comprobante Fiscal SUNAT
        Facturacion->>Facturacion: Emite PDF descargable con iText
    end
```

---

## â“ Banco de Preguntas TÃ­picas del Profesor y Respuestas Clave

### 1. Â¿Por quÃ© usamos *Database per Service* y cÃ³mo evitamos bloqueos de base de datos?
> **Respuesta:** Cada microservicio es dueÃ±o exclusivo de su esquema de PostgreSQL (tenemos 8 bases de datos independientes en el clÃºster). NingÃºn microservicio tiene credenciales ni permisos de red para hacer consultas cruzadas o JOINs directos sobre la base de otro servicio. La comunicaciÃ³n se realiza exclusivamente vÃ­a contratos HTTP (OpenFeign) para operaciones Síncronas o eventos Kafka para operaciones aSíncronas.

### 2. Â¿CÃ³mo manejan la consistencia distribuida si falla el pago?
> **Respuesta:** Implementamos el patrÃ³n **Saga Coreografiada**. Cuando `orden-ms` emite `orden.creada`, los medicamentos quedan reservados. Si `pago-ms` rechaza la transacciÃ³n (fondos insuficientes o timeout), emite `pago.fallido`. `orden-ms` consume este evento compensatorio, marca la orden como `CANCELADA` y envÃ­a una peticiÃ³n compensatoria a `inventario-ms` para devolver el stock a su lote original.

### 3. Â¿CÃ³mo garantizan que un mensaje en Kafka no se procese dos veces (Idempotencia)?
> **Respuesta:** En `pago-ms`, la tabla `pagos` cuenta con una restricciÃ³n de unicidad a nivel de base de datos: `CONSTRAINT uk_pagos_orden UNIQUE (orden_id)`. AdemÃ¡s, el consumidor consulta primero si el `orden_id` ya fue procesado antes de invocar a la pasarela de pagos, descartando mensajes duplicados sin efectos colaterales.

### 4. Â¿QuÃ© sucede si `catalogo-ms` estÃ¡ caÃ­do cuando se intenta crear una orden?
> **Respuesta:** La llamada OpenFeign estÃ¡ envuelta con un **Circuit Breaker de Resilience4j**. Si `catalogo-ms` no responde dentro del umbral configurado (`timeoutDuration: 2s`), se ejecuta el mÃ©todo de fallback que responde con datos en cachÃ© o una respuesta controlada de degradaciÃ³n elegante, impidiendo el efecto dominÃ³ (cascading failure).

### 5. Â¿CÃ³mo se protegen los microservicios y quiÃ©n valida el JWT?
> **Respuesta:** Aplicamos seguridad en capas (Defense in Depth). El **API Gateway** realiza la primera validaciÃ³n perimetral (formato, expiraciÃ³n y firma criptogrÃ¡fica). Luego, cada microservicio de negocio actÃºa como un **OAuth2 Resource Server** independiente que extrae los `authorities` del token y aplica control de acceso granular con `@PreAuthorize("hasRole('ROLE_FARMACEUTICO')")`.

---

## ðŸ› ï¸ Comandos de DemostraciÃ³n en Terminal

```bash
# 1. Comprobar que los 18 contenedores estÃ¡n arriba y sanos
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 2. Verificar las 8 bases de datos PostgreSQL creadas
docker exec -it medizano-postgres psql -U postgres -d postgres -c "\l"

# 3. Inspeccionar los topics de Kafka directamente en consola
docker exec -it medizano-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# 4. Probar la salud de un microservicio a travÃ©s del Gateway
curl -s http://localhost:8090/actuator/health

# 5. Ejecutar la auditorÃ­a funcional completa (10/10 OK)
node run_e2e_audit.js
```
