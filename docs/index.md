# MediZano POS - Sistema Distribuido Farmacéutico

> **Curso:** Desarrollo de Aplicaciones Distribuidas (DIST) · **Periodo:** 2026-2 · **Institución:** UPeU  
> **Repositorio Oficial:** [https://github.com/rbn-69-cod/MediZano_microservicios](https://github.com/rbn-69-cod/MediZano_microservicios)

---

<div class="host-switcher-card" style="background: var(--md-code-bg-color, #f8fafc); border: 2px solid #0d9488; border-radius: 12px; padding: 18px 22px; margin: 20px 0 28px 0; box-shadow: 0 4px 14px rgba(0,0,0,0.06);">
  <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-bottom: 12px;">
    <div style="display: flex; align-items: center; gap: 8px;">
      <span style="font-size: 22px;">🌐</span>
      <strong style="font-size: 16px;">Tablero de Enlaces Directos en Vivo (1-Click Demo)</strong>
    </div>
    <div style="background: #0f172a; color: #38bdf8; padding: 4px 14px; border-radius: 20px; font-family: monospace; font-size: 13.5px; font-weight: 700; border: 1px solid #0284c7;">
      IP / Host Activo: <span id="medizano-current-host-display">Detectando...</span>
    </div>
  </div>

  <p style="margin: 0 0 14px 0; font-size: 13.5px; color: var(--md-default-fg-color--light, #475569); line-height: 1.5;">
    Detecta automáticamente la IP pública de AWS EC2 o cambia a Localhost con 1 solo clic. Si reinicias la máquina y AWS te asigna una nueva IP, ingrésala abajo y pulsa <b>Guardar</b> para autocompletar todos los enlaces y puertos del sistema.
  </p>

  <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 18px;">
    <button id="btn-host-auto" type="button" style="background: #0d9488; color: white; border: none; border-radius: 6px; padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: 0.2s;">
      ☁️ Auto-Detectar IP de AWS
    </button>
    <button id="btn-host-local" type="button" style="background: #475569; color: white; border: none; border-radius: 6px; padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: 0.2s;">
      💻 Modo Localhost (127.0.0.1)
    </button>
    <div style="display: flex; align-items: center; gap: 6px; flex-grow: 1; max-width: 440px;">
      <input type="text" id="medizano-custom-ip-input" placeholder="O escribe la nueva IP pública (ej: 35.168.17.123)..." style="flex-grow: 1; padding: 8px 12px; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 13px; font-family: monospace;" />
      <button id="btn-host-save" type="button" style="background: #2563eb; color: white; border: none; border-radius: 6px; padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; white-space: nowrap;">
        💾 Guardar
      </button>
    </div>
  </div>

  <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; margin-top: 10px;">
    <a href="http://35.168.17.123/" data-service-port="80" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #0d9488; font-size: 14px;">🛒 Frontend POS (Puerto 80)</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123/</div>
    </a>
    <a href="http://35.168.17.123:4200/" data-service-port="4200" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #0284c7; font-size: 14px;">💻 Frontend (Puerto 4200)</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:4200/</div>
    </a>
    <a href="http://35.168.17.123:8090/swagger-ui.html" data-service-port="8090" data-service-path="/swagger-ui.html" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #16a34a; font-size: 14px;">📑 Swagger UI Centralizado</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:8090/swagger-ui.html</div>
    </a>
    <a href="http://35.168.17.123:8761/" data-service-port="8761" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #9333ea; font-size: 14px;">🔭 Eureka Service Discovery</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:8761/</div>
    </a>
    <a href="http://35.168.17.123:18085/" data-service-port="18085" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #ea580c; font-size: 14px;">📨 Kafka UI (Kafbat Broker)</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:18085/</div>
    </a>
    <a href="http://35.168.17.123:3000/" data-service-port="3000" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #d97706; font-size: 14px;">📊 Grafana (admin / admin)</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:3000/</div>
    </a>
    <a href="http://35.168.17.123:9090/" data-service-port="9090" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #dc2626; font-size: 14px;">📈 Prometheus Server</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:9090/</div>
    </a>
    <a href="http://35.168.17.123:8025/" data-service-port="8025" data-service-path="/" target="_blank" style="display: block; text-decoration: none; background: var(--md-card-bg-color, white); border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; color: inherit; transition: all 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.05);" onmouseover="this.style.borderColor='#0d9488';this.style.transform='translateY(-2px)'" onmouseout="this.style.borderColor='#cbd5e1';this.style.transform='translateY(0)'">
      <div style="font-weight: 700; color: #0284c7; font-size: 14px;">📬 Mailpit Webmail</div>
      <div class="service-url-badge" style="font-size: 11.5px; font-family: monospace; color: #64748b; margin-top: 4px; word-break: break-all;">http://35.168.17.123:8025/</div>
    </a>
  </div>
  
  <div style="margin-top: 14px; padding-top: 12px; border-top: 1px dashed #cbd5e1; display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px;">
    <span>🎯 <b>Guía de Sustentación:</b> <a href="proyecto-sello/guia-exposicion.md">Abrir Guía Minuto a Minuto</a></span>
    <span>📋 <b>Guía de Construcción:</b> <a href="proyecto-sello/guia-construccion-paso-a-paso.md">Paso a Paso desde Cero</a></span>
  </div>
</div>

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
