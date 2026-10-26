# MediZano POS - Sistema Distribuido Farmacéutico

> **Curso:** Desarrollo de Aplicaciones Distribuidas (DIST) · **Periodo:** 2026-2 · **Institución:** UPeU  
> **Repositorio Oficial:** [https://github.com/rbn-69-cod/MediZano_microservicios](https://github.com/rbn-69-cod/MediZano_microservicios)

---

> [!IMPORTANT]
> ### Tablero de Enlaces Directos en Vivo (1-Click Demo)
> Durante la sustentación, abre directamente cualquiera de las interfaces operativas:
> 
> * **Frontend Web POS (Angular 17):** [http://localhost:4200/](http://localhost:4200/)
> * **Swagger UI Centralizado (API Gateway):** [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
> * **Eureka Service Discovery:** [http://localhost:8761/](http://localhost:8761/)
> * **Kafka UI (Broker KRaft y Topics):** [http://localhost:18085/](http://localhost:18085/)
> * **Grafana Dashboards:** [http://localhost:3000/](http://localhost:3000/) *(Credenciales: admin / admin)*
> * **Prometheus Server:** [http://localhost:9090/](http://localhost:9090/)
> * **Mailpit Webmail (Simulador SMTP de Recibos):** [http://localhost:8025/](http://localhost:8025/)
> * **Guía Detallada de Exposición (Minuto a Minuto):** [Abrir Guía de Sustentación](proyecto-sello/guia-exposicion.md)
> * **Guía de Construcción Paso a Paso:** [Abrir Guía Paso a Paso](proyecto-sello/guia-construccion-paso-a-paso.md)

---

## Repositorio Oficial GitHub y Estructura Distribuida

* **URL de Origen:** [https://github.com/rbn-69-cod/MediZano_microservicios.git](https://github.com/rbn-69-cod/MediZano_microservicios.git)
* **Rama Principal:** `main` | **Arquitectura:** Clúster de Microservicios Contenerizados con Docker Compose

![Arquitectura General MediZano POS](assets/images/arquitectura-medizano.png)

---

## Reparto Oficial por Integrante (4 Integrantes x 2 Microservicios)

Cumpliendo con la directiva estricta de la cátedra (**1 microservicio Transaccional Cabecera-Detalle + 1 microservicio No Transaccional Catálogo/Maestro por estudiante**):

| N° | Estudiante | Microservicio Transaccional (Cabecera - Detalle) | Microservicio No Transaccional (Catálogo / Maestro) | Responsabilidad en la Sustentación |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Kenyi Calsin** | **`orden-ms`** (`:8082`)<br>• Tablas: `ordenes` + `orden_detalles`<br>• Operación POS cabecera-detalle | **`cliente-ms`** (`:8084`)<br>• Tabla: `clientes`<br>• Maestro DNI/RUC con validación | Comunicación Síncrona Resiliente con **OpenFeign** y **Resilience4j Circuit Breaker**. |
| **2** | **Aldo Calla** | **`inventario-ms`** (`:8085`)<br>• Tablas: `movimientos_inventario` + `movimiento_detalles`<br>• Kardex y Lotes FEFO | **`catalogo-ms`** (`:8081`)<br>• Tablas: `medicamentos` + `categorias`<br>• 203 medicamentos maestros | Algoritmo **FEFO** (First Expired, First Out) y trazabilidad farmacéutica DIGEMID. |
| **3** | **Igarlos Mamani** | **`pago-ms`** (`:8083`)<br>• Tablas: `pagos` con idempotencia única<br>• Transacción Mercado Pago / PayPal | **`usuario-ms`** (`:8087`)<br>• Tablas: `usuarios` + `roles`<br>• Servidor de identidad y token JWT | Seguridad perimetral JWT/RBAC y mensajería orientada a eventos con **Apache Kafka**. |
| **4** | **Juan Chambilla** | **`facturacion-ms`** (`:8086`)<br>• Tablas: `facturas` + `factura_items`<br>• Comprobante fiscal con IGV 18% | **`proveedor-ms`** (`:8088`)<br>• Tablas: `proveedores` DIGEMID<br>• Módulo de Compras a Laboratorios | Integración tributaria SUNAT, generación de Boletas/Facturas y Abastecimiento. |

---

### Integrante 1: Kenyi Calsin
**Rol:** Arquitectura de Orquestación de Pedidos y Resiliencia Síncrona.

* **Microservicio Transaccional:** `orden-ms` (Puerto `:8082` | BD: `medizano_ordenes_db`)
    * **Tablas implementadas:** `ordenes` (Cabecera: ID, cliente, método pago, estado, total) y `orden_detalles` (Detalle: ID, orden_id, medicamento_id, cantidad, precio, subtotal).
    * **Endpoints clave:** `POST /api/v1/ordenes` (Creación de venta POS), `GET /api/v1/ordenes/{id}`.
    * **Técnica defensiva:** Comunicación síncrona mediante **OpenFeign** hacia `catalogo-ms` e `inventario-ms` protegida con **Resilience4j Circuit Breaker y Timeouts (2s)**.
    * **Evento Kafka:** Emite el evento asíncrono `orden.creada` al topic `orden-eventos`.
* **Microservicio No Transaccional:** `cliente-ms` (Puerto `:8084` | BD: `medizano_clientes_db`)
    * **Tablas implementadas:** `clientes` (ID, tipo_persona, DNI, RUC, nombre_completo, teléfono, email, dirección).
    * **Endpoints clave:** `GET /api/v1/clientes/documento/{doc}`, `POST /api/v1/clientes`.
* **Defensa ante el profesor:** *"Explico cómo `orden-ms` no hace JOINs con clientes ni medicamentos (Database per Service); valida los datos con OpenFeign y si el servicio catálogo falla, el Circuit Breaker de Resilience4j ejecuta el fallback sin tumbar la pasarela POS"*.

---

### Integrante 2: Aldo Calla
**Rol:** Control de Inventario Kardex FEFO y Catálogo Farmacéutico.

* **Microservicio Transaccional:** `inventario-ms` (Puerto `:8085` | BD: `medizano_inventario_db`)
    * **Tablas implementadas:** `lotes` (lote, fecha_vencimiento, stock_actual, precio_compra) y `movimientos_inventario` (tipo: ENTRADA, SALIDA_VENTA, COMPENSACION, DEVOLUCION).
    * **Endpoints clave:** `POST /api/pharmacist/batches`, `GET /api/pharmacist/batches/medicine/{id}`, `POST /api/v1/inventario/reservar`.
    * **Técnica defensiva:** Algoritmo **FEFO (First Expired, First Out)** que selecciona automáticamente el lote con fecha de caducidad más cercana para la dispensación farmacéutica.
    * **Compensación Saga:** Endpoint de liberación y reposición de stock ante fallos de pago.
* **Microservicio No Transaccional:** `catalogo-ms` (Puerto `:8081` | BD: `medizano_catalogo_db`)
    * **Tablas implementadas:** `medicamentos` (nombre, principio activo, concentración, forma, código de barras) y `categorias` (Analgésicos, Antibióticos, etc.).
    * **Endpoints clave:** `GET /api/pharmacist/medicines`, `GET /api/pharmacist/medicines/barcode/{barcode}`.
* **Defensa ante el profesor:** *"En una farmacia la regla crítica es FEFO, no FIFO. Si un lote vence antes, se descuenta primero. Si la orden se cancela, `inventario-ms` ejecuta una compensación restaurando el stock exacto al lote reservado"*.

---

### Integrante 3: Igarlos Mamani
**Rol:** Seguridad Distribuida (JWT/RBAC), Pasarelas de Pago y Mensajería Kafka.

* **Microservicio Transaccional:** `pago-ms` (Puerto `:8083` | BD: `medizano_pagos_db`)
    * **Tablas implementadas:** `pagos` (ID, orden_id, transaction_id, gateway: PAYPAL/MERCADOPAGO, monto, estado).
    * **Endpoints clave:** `POST /api/v1/pagos/checkout`, `POST /api/v1/pagos/webhook/mercadopago`.
    * **Técnica defensiva:** **Consumidor Kafka Idempotente**. Protección con `CONSTRAINT uk_pagos_orden UNIQUE (orden_id)` para asegurar que ningún pedido se cobre dos veces ante reintentos de red.
    * **Evento Kafka:** Consume `orden.creada` y emite `pago.validado` o `pago.fallido`.
* **Microservicio No Transaccional:** `usuario-ms` (Puerto `:8087` | BD: `medizano_usuarios_db`)
    * **Tablas implementadas:** `usuarios` (username, password_hash BCrypt, email, activo) y `roles` (`ROLE_ADMIN`, `ROLE_FARMACEUTICO`, `ROLE_CLIENTE`).
    * **Endpoints clave:** `POST /api/auth/login`, `GET /api/admin/audit/all`.
* **Defensa ante el profesor:** *"Demuestro que la seguridad no es solo de Gateway. Cada microservicio valida criptográficamente el JWT con su clave secreta y aplica `@PreAuthorize`. Además `pago-ms` tiene idempotencia garantizada por base de datos"*.

---

### Integrante 4: Juan Chambilla
**Rol:** Facturación Fiscal SUNAT, Comprobantes Electrónicos y Abastecimiento de Proveedores.

* **Microservicio Transaccional:** `facturacion-ms` (Puerto `:8086` | BD: `medizano_facturas_db`)
    * **Tablas implementadas:** `facturas` (Cabecera: serie, correlativo, cliente, subtotal, IGV 18%, total) y `factura_items` (Detalle: producto, cantidad, precio, IGV línea).
    * **Endpoints clave:** `POST /api/v1/facturas`, `GET /api/v1/facturas/{id}/pdf`.
    * **Técnica defensiva:** Emisión asíncrona desacoplada mediante eventos Kafka (`pago.validado`). Correlativos fiscales independientes para Boletas (`B001-XXXX`) y Facturas (`F001-XXXX`).
* **Microservicio No Transaccional:** `proveedor-ms` (Puerto `:8088` | BD: `medizano_proveedores_db`)
    * **Tablas implementadas:** `proveedores` (RUC 11 dígitos, razón social, laboratorio, registro DIGEMID, activo) y órdenes de compra con recepciones atómicas.
    * **Endpoints clave:** `GET /api/v1/proveedores`, `POST /api/v1/proveedores`, `GET /api/v1/compras`.
* **Defensa ante el profesor:** *"Explico cómo `facturacion-ms` no bloquea la caja registradora. Al confirmarse el pago por Kafka, el comprobante se genera en segundo plano con el desglose exacto de 18% de IGV e ingresa a almacén FEFO"*.

---

## Galería Visual de Evidencias en Vivo

### 1. Frontend Web POS (Angular 17)
Interfaz completa de dispensación, búsqueda de medicamentos por código de barras, selección de paciente y cobro digital:
![Pantalla POS Angular](assets/images/medizano-frontend.png)

### 2. Service Discovery Dinámico (Netflix Eureka Server)
Las 8 instancias de microservicios registradas dinámicamente con sus IPs y puertos internos en `http://localhost:8761`:
![Instancias en Eureka Server](assets/images/eureka-instances.png)

### 3. Mensajería Asíncrona (Kafbat UI - Apache Kafka)
Tópicos de mensajería (`orden-eventos`, `pago-eventos`, `inventario-eventos`, `factura-eventos`) en `http://localhost:18085`:
![Panel Kafka UI](assets/images/kafka-topics-detail.png)

### 4. Observabilidad Centralizada (Grafana Dashboards)
Tableros de métricas JVM, throughput de peticiones HTTP, estado de base de datos y trazabilidad de logs en `http://localhost:3000`:
![Dashboard de Grafana](assets/images/grafana-dashboard.png)

### 5. Documentación Interactiva de Endpoints (Swagger UI)
Punto único perimetral con documentación OpenAPI 3.0 de todos los microservicios en `http://localhost:8090/swagger-ui.html`:
![Swagger Gateway](assets/images/swagger-gateway.png)

### 6. Métricas de Rendimiento en Prometheus
Scraping continuo de métricas en los endpoints `/actuator/prometheus` de cada contenedor en `http://localhost:9090`:
![Métricas en Prometheus](assets/images/prometheus-targets.png)

---

## Matriz de Credenciales y Roles de Acceso (RBAC)

El sistema cuenta con 7 usuarios preconfigurados para demostrar la seguridad distribuida:

| Usuario | Contraseña | Rol Asignado | Email | Responsabilidad en la Botica | Rutas Autorizadas |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **`admin`** | `admin123` | `ADMIN` | `admin@medizano.pe` | **Administrador General:** Auditoría completa, gestión de usuarios, roles, métricas y configuración perimetral. | `/dashboard`, `/user-management`, `/user-activity`, `/login-history`, y todas las rutas. |
| **`cajero`** | `admin123` | `CASHIER` | `cajero@medizano.pe` | **Punto de Venta (POS):** Búsqueda de medicamentos por código de barras, emisión de órdenes, cobro con pasarelas (PayPal/Mercado Pago) y facturación. | `/dashboard`, `/billing` |
| **`inventario`** | `admin123` | `STOCK_MONITOR` | `inventario@medizano.pe` | **Control de Existencias:** Monitoreo de stock, visualización de lotes activos y control de vencimiento farmacéutico bajo regla FEFO. | `/dashboard`, `/inventory` |
| **`almacen`** | `admin123` | `STOCK_KEEPER` | `almacen@medizano.pe` | **Logística Farmacéutica:** Catálogo de medicamentos, registro de ingresos por compras a laboratorios y actualización de lotes. | `/dashboard`, `/medicines` |
| **`soporte`** | `admin123` | `CUSTOMER_SUPPORT` | `soporte@medizano.pe` | **Atención al Cliente:** Directorio de pacientes/clientes, consulta de recetas, historial de órdenes y registro de devoluciones/reembolsos. | `/dashboard`, `/returns` |
| **`analista`** | `admin123` | `ANALYST` | `analista@medizano.pe` | **Reportes y Finanzas:** Reportes consolidados de ventas, conciliación de caja, declaraciones tributarias SUNAT y analítica. | `/dashboard`, `/reports` |
| **`gerente`** | `admin123` | `MANAGER` | `gerente@medizano.pe` | **Supervisión Gerencial:** Aprobación de devoluciones, auditoría gerencial de arqueo de caja y contratos con proveedores DIGEMID. | `/dashboard`, `/purchase-history`, `/reports` |

![Login con Roles](assets/images/medizano-login.png)

---

## Flujo de Demostración End-to-End en Exposición

El siguiente paso a paso representa la venta completa que se realiza en vivo ante el jurado:

1. **Paso 1 (Autenticación):** El cajero inicia sesión en `http://localhost:4200/` con `cajero` / `admin123`. El API Gateway valida las credenciales contra `usuario-ms` y emite un token JWT.
2. **Paso 2 (Escaneo de Medicamento):** En la pantalla de Punto de Venta, se busca el medicamento por código de barras (`7751234567890`). `catalogo-ms` retorna *Paracetamol 500 mg*.
3. **Paso 3 (Creación de Orden):** Se añade al carrito y se confirma la orden. `orden-ms` consulta por OpenFeign a `inventario-ms` para reservar el lote más próximo a vencer (**Regla FEFO**).
4. **Paso 4 (Evento Kafka y Cobro):** `orden-ms` emite `orden.creada` a Kafka. `pago-ms` procesa el cobro con idempotencia y emite `pago.validado`.
5. **Paso 5 (Facturación Automática):** `facturacion-ms` consume el evento y emite la Boleta Electrónica con el cálculo de 18% de IGV y correlativo fiscal.
6. **Paso 6 (Verificación en Dashboards):** Se abre Grafana (`:3000`) y Kafka UI (`:18085`) para mostrar al jurado el evento registrado en tiempo real.
