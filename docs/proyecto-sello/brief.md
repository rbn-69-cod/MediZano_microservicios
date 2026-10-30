# Brief Técnico del Proyecto Sello: MediZano POS

Este documento es el hito oficial de **S2** del curso de **Desarrollo de Aplicaciones Distribuidas (UPeU 2026-2)**: el acta técnica donde el equipo declara la arquitectura, roles, distribución de microservicios y alcance del sistema distribuido antes de avanzar al registro y descubrimiento.

---

## 1. Datos del Equipo

* **Nombre del equipo:** MediZano DevTeam
* **Sección:** Grupo 1
* **Repositorio:** `https://github.com/upeu-distribuidas/medizano`
* **Topics configurados en GitHub:** `distribuidas-2026-2`, `grupo-medizano`, `spring-cloud`, `microservices`, `pos-pharmacy`

### Integrantes y Énfasis Técnico (4 Integrantes)

| N° | Integrante | Rol y Énfasis Técnico en la Arquitectura |
| :---: | :--- | :--- |
| **1** | **Kenyi Calsin** | Coordinador de Ventas & Clientes. Énfasis: Comunicación Síncrona Resiliente (OpenFeign + Resilience4j) y Orquestación de Pedidos. |
| **2** | **Aldo Calla** | Especialista en Farmacología & Stock. Énfasis: Gestión Transaccional de Inventarios Kardex, Lotes FEFO y Catálogo Maestro. |
| **3** | **Igarlos Mamani** | Especialista en Pasarelas de Pago & Seguridad. Énfasis: Integración con APIs externas (PayPal / Mercado Pago), JWT asimétrico y Mensajería Kafka. |
| **4** | **Juan Chambilla** | Especialista en Facturación Fiscal & Proveedores. Énfasis: Comprobantes electrónicos SUNAT con desglose de IGV y Cadena de Suministro. |

---

## 2. Dominio del Proyecto

* **Nombre del proyecto:** MediZano POS - Sistema Distribuido de Gestión Farmacéutica y Punto de Venta.
* **Problema o necesidad que resuelve:** La dispensación y venta de medicamentos requiere alta trazabilidad (lotes, fechas de vencimiento, recetas médicas y control estricto de stock) junto con una operación de caja rápida en mostrador, tolerante a caídas de red, con cobros digitales seguros y facturación electrónica válida ante la autoridad tributaria.
* **Dominio de negocio:** Comercio farmacéutico minorista (POS y e-commerce). Flujo de valor: Catálogo maestro de medicamentos $\rightarrow$ Generación de oorden de compra en mostrador o web $\rightarrow$ Procesamiento de pago digital o en caja $\rightarrow$ Facturación electrónica $\rightarrow$ Despacho y descuento de lotes en inventario con trazabilidad Kardex.
* **Usuarios y roles principales:**
  * `ROLE_ADMIN`: Administrador general (gestión de usuarios, inventario maestro, reportes).
  * `ROLE_FARMACEUTICO`: Personal de atención en mostrador (ventas, despacho de recetas, caja).
  * `ROLE_CLIENTE`: Paciente o comprador final (consulta de catálogo, óóordenes en línea y pagos).
* **Servicio externo real integrado:**
  * **Pasarela de Pagos Externa Real:** **Mercado Pago** (Checkout Pro & Webhooks) y **PayPal Sandbox** con credenciales reales para procesamiento y confirmación de cobros digitales en vivo.
  * **Servicio de Validación de Identidad:** Consulta de DNI (RENIEC) y RUC (SUNAT) para clientes y emisión fiscal.

---

## 3. Asignación de Microservicios por Integrante (Regla Oficial del Sílabo)

> **Regla de asignación obligatoria:** Cada integrante es responsable de **dos microservicios**:
> 1. **Un microservicio Transaccional:** Con operación cabecera-detalle real, persistencia ACID local y reglas de negocio complejas (equivalente a `oorden-ms` o `pago-ms`).
> 2. **Un microservicio No Transaccional:** Con operaciones CRUD maestro, catálogos o entidades independientes (equivalente a `catalogo-ms` o `cliente-ms`).
>
> Ningún integrante puede tener dos microservicios no transaccionales. Además, **todos los microservicios validan tokens JWT de forma distribuida**.

| N° | Integrante | Microservicio Transaccional (Cabecera - Detalle) | Microservicio No Transaccional (Catálogo / Maestro) |
| :---: | :--- | :--- | :--- |
| **1** | **Kenyi Calsin** | **`oorden-ms`** (`:8082`) · Venta POS / Carrito con items | **`cliente-ms`** (`:8084`) · Pacientes y Directorio |
| **2** | **Aldo Calla** | **`inventario-ms`** (`:8085`) · Movimientos Kardex y Lotes | **`catalogo-ms`** (`:8081`) · Medicamentos y Categorías |
| **3** | **Igarlos Mamani** | **`pago-ms`** (`:8083`) · Transacción de cobro y pasarelas | **`usuario-ms`** (`:8087`) · Autenticación, Roles y JWT |
| **4** | **Juan Chambilla** | **`facturacion-ms`** (`:8086`) · Comprobante fiscal con items | **`proveedor-ms`** (`:8088`) · Laboratorios y Proveedores |

---

## 4. Fichas Técnicas por Microservicio (8 Microservicios)

---

### Ficha 1: `oorden-ms` (Kenyi Calsin · Tipo: Transaccional)
* **Descripción breve:** Orquesta el proceso de venta de medicamentos. Recibe la solicitud de compra, verifica disponibilidad síncrona mediante Feign, persiste la oorden con estado inicial `PENDIENTE_PAGO` y publica el evento `oorden.creada` a Kafka.
* **Estructura Cabecera-Detalle:**
  * **Cabecera (`oóordenes`):** `id`, `id_cliente`, `fecha_creacion`, `estado` (`PENDIENTE_PAGO`, `PAGADA`, `CANCELADA`), `metodo_pago`, `total`.
  * **Detalle (`oorden_detalles`):** `id`, `id_oorden` (FK), `id_medicamento`, `nombre_medicamento`, `cantidad`, `precio_unitario`, `subtotal`.
* **Endpoints previstos:**
  * `POST /api/v1/oóordenes`: Crea una oorden de venta.
  * `GET /api/v1/oóordenes`: Lista las óóordenes registradas o del usuario autenticado.
  * `GET /api/v1/oóordenes/{id}`: Detalle completo del pedido y estado de pago.
* **Comunicación con otros microservicios:**
  * **Síncrona (S6):** Consume `catalogo-ms` e `inventario-ms` vía OpenFeign con Resilience4j (Circuit Breaker y Fallback) para validar precios y reservar existencias.
  * **Asíncrona (S8-S9):** Publica `oorden.creada` en Kafka (`oorden-eventos`). Consume `pago.validado` y `pago.fallido` de `pago-eventos` para transición de estados y compensación.
* **Seguridad y Rutas Protegidas:**
  * `POST /api/v1/oóordenes`: Requiere `ROLE_CLIENTE` o `ROLE_FARMACEUTICO`. El `id_cliente` se extrae del JWT (`sub`).
  * `GET /api/v1/oóordenes`: Requiere token válido.
* **Requisitos funcionales:**
  1. El sistema debe calcular automáticamente el total de la oorden sumando los subtotales de cada detalle.
  2. El sistema debe rechazar la oorden si la llamada a catálogo o inventario indica stock insuficiente.
  3. El sistema debe cambiar el estado a `CANCELADA` y ejecutar compensación si recibe el evento `pago.fallido`.

---

### Ficha 2: `cliente-ms` (Kenyi Calsin · Tipo: No Transaccional)
* **Descripción breve:** Administra los perfiles de pacientes y clientes, su información de contacto, números de documento (DNI / RUC), tipo de seguro médico o afiliación y direcciones para envíos.
* **Entidad principal:**
  * **`Cliente` (`clientes`):** `id`, `tipo_persona` (`NATURAL`, `JURIDICA`), `dni` (UNIQUE), `ruc` (UNIQUE), `nombre_completo`, `razon_social`, `email`, `telefono_whatsapp`, `direccion`, `alergias_conocidas`.
* **Endpoints previstos:**
  * `GET /api/v1/clientes`: Listar clientes registrados.
  * `GET /api/v1/clientes/por-documento/{doc}`: Buscar cliente por DNI o RUC.
  * `POST /api/v1/clientes`: Registrar un nuevo paciente o cliente.
  * `PUT /api/v1/clientes/{id}`: Actualizar datos de contacto.
* **Comunicación con otros microservicios:**
  * Atiende consultas REST de `oorden-ms` y `facturacion-ms` para obtener datos de comprobante.
* **Seguridad y Rutas Protegidas:**
  * Todas las rutas requieren token JWT (`ROLE_CLIENTE` para su propio perfil; `ROLE_FARMACEUTICO` o `ROLE_ADMIN` para el listado general).
* **Requisitos funcionales:**
  1. El sistema debe asegurar que no se repita el DNI ni el RUC en la base de datos.
  2. El sistema debe validar que los DNI tengan 8 dígitos numéricos y los RUC 11 dígitos.
  3. El sistema debe registrar el WhatsApp para el posterior envío automatizado de boletas y recordatorios.

---

### Ficha 3: `inventario-ms` (Aldo Calla · Tipo: Transaccional)
* **Descripción breve:** Controla el inventario físico, existencias por almacén, movimientos Kardex y la gestión de **Lotes (`Batch`) con Fechas de Vencimiento**, asegurando que los medicamentos próximos a vencer se despachen primero (regla FEFO).
* **Estructura Cabecera-Detalle:**
  * **Cabecera (`movimientos_inventario`):** `id`, `tipo_movimiento` (`ENTRADA_COMPRA`, `SALIDA_VENTA`, `AJUSTE_MERMA`, `COMPENSACION_CANCELACION`), `fecha_movimiento`, `origen_destino`, `usuario_id`, `motivo`.
  * **Detalle (`movimiento_detalles`):** `id`, `movimiento_id` (FK), `medicamento_id`, `lote_id`, `cantidad`, `stock_anterior`, `stock_posterior`.
  * **Entidad de Control de Lotes (`lotes`):** `id`, `medicamento_id`, `numero_lote`, `fecha_vencimiento`, `stock_actual`.
* **Endpoints previstos:**
  * `POST /api/v1/inventario/movimientos`: Registrar entrada o salida manual de stock con cabecera-detalle.
  * `PATCH /api/v1/inventario/{medicamentoId}/descontar?cantidad=X`: Descuenta stock del lote más próximo a vencer (FEFO).
  * `PATCH /api/v1/inventario/{medicamentoId}/restaurar?cantidad=X`: Restaura stock si una oorden se cancela (Compensación Saga).
  * `GET /api/v1/inventario/lotes/por-vencer`: Listado de medicamentos con vencimiento menor a 60 días.
* **Comunicación con otros microservicios:**
  * **Síncrona (S6):** Atiende peticiones de descuento y validación de `oorden-ms` mediante OpenFeign.
  * **Compensación:** Endpoint PATCH para revertir stock ante fallo de pago.
* **Seguridad y Rutas Protegidas:**
  * Modificaciones de stock requieren `ROLE_FARMACEUTICO` o `ROLE_ADMIN`.
* **Requisitos funcionales:**
  1. El sistema no permite despachar medicamentos cuyo lote ya haya superado la fecha de vencimiento.
  2. Todo cambio en el stock físico debe quedar registrado en un movimiento Kardex con su respectivo detalle de auditoría.
  3. Soporta operaciones transaccionales atómicas para evitar inconsistencias de inventario por concurrencia.

---

### Ficha 4: `catalogo-ms` (Aldo Calla · Tipo: No Transaccional)
* **Descripción breve:** Administra el catálogo maestro de medicamentos farmacéuticos, principios activos, laboratorios fabricantes, formas de presentación (tabletas, jarabes, inyectables) y precios base.
* **Entidad principal:**
  * **`Medicine` (`medicamentos`):** `id`, `nombre_comercial`, `principio_activo`, `concentracion`, `presentacion`, `categoria_id`, `precio_venta`, `requiere_receta` (boolean), `activo`.
  * **`Categoria` (`categorias`):** `id`, `nombre`, `descripcion`.
* **Endpoints previstos:**
  * `GET /api/v1/medicamentos`: Listado público de medicamentos activos.
  * `GET /api/v1/medicamentos/{id}`: Detalle de medicamento por ID.
  * `POST /api/v1/medicamentos`: Registro de nuevo medicamento en catálogo.
  * `PUT /api/v1/medicamentos/{id}`: Actualización de datos farmacológicos y precio.
* **Comunicación con otros microservicios:**
  * Expone endpoints REST para que `oorden-ms` y `facturacion-ms` consulten datos de productos.
* **Seguridad y Rutas Protegidas:**
  * `GET /api/v1/medicamentos/**`: Endpoint público (sin autenticación requerida para exploración).
  * `POST`, `PUT`, `DELETE`: Protegidos con `ROLE_ADMIN` o `ROLE_FARMACEUTICO`.
* **Requisitos funcionales:**
  1. El sistema debe permitir búsquedas de medicamentos por nombre comercial o por principio activo.
  2. El sistema debe advertir si un medicamento está marcado como `requiere_receta: true`.
  3. El sistema debe mantener borrado lógico (`activo = false`) para conservar el historial de ventas.

---

### Ficha 5: `pago-ms` (Igarlos Mamani · Tipo: Transaccional)
* **Descripción breve:** Procesa cobros digitales integrando pasarelas externas reales (Mercado Pago / PayPal) y pagos en efectivo en caja. Consume eventos de óóordenes de Kafka y emite eventos de validación o rechazo con garantía de idempotencia.
* **Estructura Cabecera-Detalle / Transacción:**
  * **`pagos`:** `id`, `oorden_id` (UNIQUE), `metodo_pago` (`MERCADOPAGO`, `PAYPAL`, `EFECTIVO`, `TARJETA`), `monto`, `moneda`, `estado` (`PENDIENTE`, `VALIDADO`, `FALLIDO`), `transaccion_externa_id`, `fecha_pago`.
* **Endpoints previstos:**
  * `POST /api/v1/pagos/crear-preferencia`: Genera la sesión de cobro real con Mercado Pago / PayPal.
  * `POST /api/v1/pagos/webhook`: Webhook para recibir notificaciones IPN asíncronas de la pasarela.
  * `GET /api/v1/pagos/por-oorden/{oordenId}`: Consulta el estado del pago asociado a una oorden.
* **Comunicación con otros microservicios:**
  * **Asíncrona (S8-S9):** Consume `oorden.creada` de `oorden-eventos`. Publica `pago.validado` o `pago.fallido` en el topic `pago-eventos`.
* **Seguridad y Rutas Protegidas:**
  * `POST /api/v1/pagos/webhook`: Público pero protegido mediante validación de firma criptográfica (`x-signature` de Mercado Pago).
  * `GET /api/v1/pagos/**`: Requiere autenticación JWT con `ROLE_CLIENTE`, `ROLE_FARMACEUTICO` o `ROLE_ADMIN`.
* **Requisitos funcionales:**
  1. El consumidor Kafka debe ser idempotente: si recibe el mismo evento `oorden.creada` dos veces, no debe duplicar el cobro ni el registro en base de datos.
  2. El sistema debe comunicarse con la API REST externa de Mercado Pago / PayPal y capturar el ID de transacción real.
  3. Si la transacción es rechazada, debe publicar inmediatamente `pago.fallido` a Kafka para activar la compensación en `oorden-ms`.

---

### Ficha 6: `usuario-ms` (Igarlos Mamani · Tipo: No Transaccional)
* **Descripción breve:** Servidor de autenticación y gestión de usuarios. Administra las credenciales, encriptación BCrypt, asignación de roles (RBAC) y emisión de JSON Web Tokens (JWT) firmados asimétricamente para todos los microservicios.
* **Entidad principal:**
  * **`User` (`usuarios`):** `id`, `username`, `password_hash`, `email`, `nombre_completo`, `rol` (`ROLE_ADMIN`, `ROLE_FARMACEUTICO`, `ROLE_CLIENTE`), `activo`, `ultimo_login`.
* **Endpoints previstos:**
  * `POST /api/v1/auth/login`: Autentica credenciales y emite token JWT con claims (`sub`, `roles`, `exp`).
  * `POST /api/v1/auth/registro`: Registro de nuevos clientes.
  * `GET /api/v1/usuarios/perfil`: Datos del usuario autenticado.
* **Comunicación con otros microservicios:**
  * Expone el endpoint de validación de tokens o JWKS para que el Gateway y demás microservicios verifiquen la firma criptográfica de los tokens.
* **Seguridad y Rutas Protegidas:**
  * `/api/v1/auth/login` y `/api/v1/auth/registro` son públicos.
  * `/api/v1/usuarios/**` requiere `ROLE_ADMIN`.
* **Requisitos funcionales:**
  1. Las contraseñas se almacenan con algoritmo de hashing seguro BCrypt (factor de costo 10+).
  2. El token JWT emitido incluye tiempo de expiración y el identificador de usuario `sub` para trazabilidad.
  3. El sistema impide el login de usuarios marcados como inactivos.

---

### Ficha 7: `facturacion-ms` (Juan Chambilla · Tipo: Transaccional)
* **Descripción breve:** Emite comprobantes de pago electrónicos con valor legal (Boletas de Venta y Facturas Electrónicas) según los estándares de SUNAT, calculando automáticamente subtotales, IGV (18%) y correlativo fiscal.
* **Estructura Cabecera-Detalle:**
  * **Cabecera (`facturas` / `comprobantes`):** `id`, `oorden_id` (UNIQUE), `tipo_comprobante` (`BOLETA`, `FACTURA`), `serie` (ej. `B001`, `F001`), `correlativo`, `id_cliente`, `nombre_receptor`, `num_documento`, `subtotal`, `igv`, `total`, `fecha_emision`, `estado` (`EMITIDO`, `ANULADO`).
  * **Detalle (`factura_items`):** `id`, `factura_id` (FK), `id_medicamento`, `descripcion`, `cantidad`, `precio_unitario`, `subtotal`, `igv_linea`.
* **Endpoints previstos:**
  * `POST /api/v1/facturas`: Emisión manual de comprobante fiscal.
  * `GET /api/v1/facturas/{id}`: Obtener comprobante con desglose tributario.
  * `GET /api/v1/facturas/por-oorden/{oordenId}`: Consulta del comprobante emitido para una oorden.
* **Comunicación con otros microservicios:**
  * **Asíncrona (S8-S9):** Consume `pago.validado` de Kafka (`pago-eventos`) para generar automáticamente el comprobante sin intervención humana.
* **Seguridad y Rutas Protegidas:**
  * Requiere `ROLE_FARMACEUTICO` o `ROLE_ADMIN` para emisión y anulación. `ROLE_CLIENTE` puede consultar sus propios comprobantes.
* **Requisitos funcionales:**
  1. El sistema debe autoincrementar de forma correlativa la numeración de Boletas (`B001-XXXX`) y Facturas (`F001-XXXX`) sin saltos de numeración.
  2. Si se solicita Factura, el cliente debe contar obligatoriamente con RUC de 11 dígitos y Razón Social.
  3. El comprobante debe almacenar el snapshot de precios e impuestos inmutable en el momento de la emisión.

---

### Ficha 8: `proveedor-ms` (Juan Chambilla · Tipo: No Transaccional)
* **Descripción breve:** Administra la cartera de proveedores farmacéuticos, distribuidoras oficiales y laboratorios (ej. Bayer, Pfizer, Genfar, Bagó), incluyendo sus registros sanitarios autorizados por DIGEMID y datos de contacto de ejecutivos comerciales.
* **Entidad principal:**
  * **`Proveedor` (`proveedores`):** `id`, `ruc` (UNIQUE), `razon_social`, `nombre_comercial`, `direccion_fiscal`, `telefono_contacto`, `email_ventas`, `registro_digemid`, `activo`.
* **Endpoints previstos:**
  * `GET /api/v1/proveedores`: Listado de laboratorios y distribuidores registrados.
  * `GET /api/v1/proveedores/{id}`: Detalle de proveedor y condiciones de suministro.
  * `POST /api/v1/proveedores`: Dar de alta un nuevo laboratorio proveedor.
  * `PUT /api/v1/proveedores/{id}`: Actualizar contacto o representante de ventas.
* **Comunicación con otros microservicios:**
  * Consultada por `inventario-ms` para vincular el ingreso de lotes de medicamentos a un proveedor formal.
* **Seguridad y Rutas Protegidas:**
  * Rutas de lectura y escritura protegidas con `ROLE_ADMIN`.
* **Requisitos funcionales:**
  1. El RUC del proveedor debe ser validado con 11 dígitos y no duplicable.
  2. El sistema debe permitir registrar el código de autorización sanitaria DIGEMID del laboratorio.
  3. Debe permitir desactivar proveedores sin eliminar las compras históricas asociadas.

---

## 5. Qué SÍ Cubre y Qué NO Cubre el Proyecto

### Lo que SÍ cubre:
* Despliegue reproducible de los 8 microservicios con Docker y Docker Compose.
* Descubrimiento dinámico de instancias vivas mediante Spring Cloud Netflix Eureka.
* Configuración centralizada de perfiles `dev` y `prod` con Spring Cloud Config Server.
* Enrutamiento perimetral, balanceo de carga y validación reactiva de tokens en Spring Cloud Gateway.
* Comunicación síncrona tolerante a fallos (Feign + Resilience4j) con Circuit Breaker, Timeout y Fallback.
* Mensajería asíncrona mediante Apache Kafka con topics `oorden-eventos` y `pago-eventos`.
* Patrón Saga Coreografiada: reserva de stock $\rightarrow$ pago con pasarela real $\rightarrow$ confirmación / compensación de inventario si el cobro es rechazado.
* Consumidores idempotentes protegidos contra duplicidad de eventos.
* Observabilidad unificada: Prometheus, Grafana, Loki y correlación de trazas mediante `traceId` en cabeceras MDC.
* Frontend Angular 17 que consume la API completa exclusivamente a través del API Gateway.

### Lo que NO cubre (Fuera de Alcance):
* Conciliación bancaria multi-cuenta contable de cierre de mes.
* Firma digital en XML con certificado digital privado de SUNAT (se simula la respuesta estructurada del facturador electrónico).
* Aplicación móvil nativa en iOS/Android (el frontend Angular es responsive para tablets y smartphones).

---

