# S12 - Evaluación y Sustentación del Producto de Unidad 2: Sistema Distribuido Robusto

> **Universidad Peruana Unión (UPeU) · Facultad de Ingeniería y Arquitectura**  
> **Curso:** Desarrollo de Aplicaciones Distribuidas  
> **Unidad 2:** Construcción de un Sistema Distribuido Robusto, Resiliente y Observado  
> **Competencia Cátedra:** CE023-N3 (Diseño, implementación y observabilidad de arquitecturas distribuidas orientadas a eventos y microservicios)

---

## 🚀 Tablero de Enlaces Directos en Vivo (Entorno de Producción AWS)

Durante la sustentación técnica en vivo frente al jurado, los siguientes servicios están desplegados y operando en la instancia EC2 de producción:

| Servicio / Herramienta | URL de Acceso en Vivo | Puerto | Credenciales por Defecto |
| :--- | :--- | :---: | :--- |
| **Punto de Venta POS (Angular 17)** | [https://54.226.25.238/](https://54.226.25.238/) | `:443` | `admin` / `admin123` |
| **Portal de Documentación MkDocs** | [http://54.226.25.238:8000/medizano/](http://54.226.25.238:8000/medizano/) | `:8000` | Acceso Público |
| **Eureka Service Discovery** | [http://54.226.25.238:8761/](http://54.226.25.238:8761/) | `:8761` | Acceso Público |
| **API Gateway (Swagger UI Central)** | [http://54.226.25.238:8090/swagger-ui.html](http://54.226.25.238:8090/swagger-ui.html) | `:8090` | Token JWT en Authorize |
| **Apache Kafka UI (Kafbat Cluster)** | [http://54.226.25.238:18085/](http://54.226.25.238:18085/) | `:18085` | Acceso Público |
| **Prometheus Metrics Server** | [http://54.226.25.238:9090/](http://54.226.25.238:9090/) | `:9090` | Acceso Público |
| **Grafana Observabilidad Centralizada** | [http://54.226.25.238:3000/](http://54.226.25.238:3000/) | `:3000` | `admin` / `admin` |

---

## 1. Propósito de la Evaluación de Unidad 2

El propósito de esta sesión es la **presentación, defensa arquitectónica y sustentación técnica en vivo** del **Producto de Unidad 2: Sistema Distribuido Robusto**. Se demuestra la madurez del sistema frente a fallos, la consistencia eventual de los datos en transacciones de negocio complejas, el aislamiento de seguridad mediante tokens criptográficos, el desacoplamiento temporal mediante mensajería por eventos y la observabilidad distribuida en tiempo real sobre los **8 microservicios del proyecto MediZano POS**.

---

## 2. Topología Arquitectónica Integral (Los 8 Microservicios)

MediZano implementa una arquitectura basada en **Database per Service**, comunicación síncrona resiliente mediante **OpenFeign + Resilience4j**, mensajería reactiva asíncrona con **Apache Kafka (KRaft)** y observabilidad centralizada con **Prometheus, Grafana y Grafana Loki**.

```mermaid
flowchart TB
    Browser["Navegador / Terminal POS"] -->|HTTPS :443| Nginx["Nginx Reverse Proxy"]
    Nginx -->|Archivos SPA| Browser
    Nginx -->|Proxy /api/**| GW["Spring Cloud Gateway :8090"]

    subgraph Infraestructura ["Infraestructura de Soporte"]
      DISC["Eureka Server :8761"]
      CONF["Config Server :8888"]
    end

    GW -.-> DISC
    GW -.-> CONF

    subgraph Backend ["Ecosistema Backend - 8 Microservicios"]
      US["usuario-ms :8087 - Roles y Sesiones"]
      CA["catalogo-ms :8081 - Medicamentos"]
      CL["cliente-ms :8084 - RUC / DNI"]
      OR["orden-ms :8082 - Órdenes y Ventas"]
      IN["inventario-ms :8085 - Stock y FEFO"]
      PA["pago-ms :8083 - Pasarelas e Idempotencia"]
      FA["facturacion-ms :8086 - SUNAT e Email"]
      PR["proveedor-ms :8088 - Compras y Lotes"]
    end

    GW --> US
    GW --> CA
    GW --> CL
    GW --> OR
    GW --> IN
    GW --> PA
    GW --> FA
    GW --> PR

    subgraph Eventos ["Event-Driven Architecture - Kafka KRaft"]
      KAFKA[("Apache Kafka :19092")]
      OR -.-> KAFKA
      KAFKA -.-> PA
      PA -.-> KAFKA
      KAFKA -.-> OR
      KAFKA -.-> FA
    end

    subgraph Observabilidad ["Plataforma de Observabilidad"]
      PROM["Prometheus :9090"]
      LOKI["Grafana Loki :3100"]
      GRAF["Grafana :3000"]
    end

    US -.-> PROM
    OR -.-> PROM
    IN -.-> PROM
    PROM --> GRAF
    LOKI --> GRAF
```

### Especificación Oficial de los 8 Microservicios del Proyecto

| N° | Microservicio | Puerto Interno | Base de Datos | Tipo | Responsabilidad de Negocio |
| :-: | :--- | :---: | :--- | :---: | :--- |
| **1** | **`usuario-ms`** | `:8087` | `medizano_usuario` | No Transaccional | Autenticación, generación y firma de JWT, control de roles (RBAC). |
| **2** | **`catalogo-ms`** | `:8081` | `medizano_catalogo` | No Transaccional | Catálogo de productos farmacéuticos, código de barras GTIN, categorías. |
| **3** | **`cliente-ms`** | `:8084` | `medizano_cliente` | No Transaccional | Directorio de clientes, validación de documentos DNI/RUC. |
| **4** | **`orden-ms`** | `:8082` | `medizano_orden` | **Transaccional** | Orquestación síncrona de ventas POS, inicio de Saga y publicación de eventos. |
| **5** | **`inventario-ms`** | `:8085` | `medizano_inventario` | **Transaccional** | Kardex, control de existencias, reservas de stock y rotación de lotes FEFO. |
| **6** | **`pago-ms`** | `:8083` | `medizano_pago` | **Transaccional** | Procesamiento de pagos, control de idempotencia y validación de cobros. |
| **7** | **`facturacion-ms`** | `:8086` | `medizano_facturacion` | No Transaccional | Generación de comprobantes de pago (Boleta/Factura 18% IGV) y envío SMTP. |
| **8** | **`proveedor-ms`** | `:8088` | `medizano_proveedor` | **Transaccional** | Gestión de proveedores, órdenes de compra y recepción de lotes farmacéuticos. |

---

## 3. Pautas y Cronograma de la Sustentación (23 Minutos)

La sustentación está rigurosamente estructurada según los lineamientos de evaluación de la cátedra:

| Etapa | Duración | Objetivo Principal | Integrantes / Roles |
| :--- | :---: | :--- | :--- |
| **1. Video Pitch / Intro** | **2 min** | Exposición concisa sobre la evolución del sistema desde el monolito/base U1 hacia un cluster de microservicios robusto en AWS. | Kenyi Calsin |
| **2. Defensa Técnica Arquitectónica** | **8 min** | Justificación de decisiones arquitectónicas y demostración de aporte de código individual (2 microservicios por integrante: 1 transaccional y 1 no transaccional). | Los 4 integrantes |
| **3. Demo Técnica en Vivo** | **8 min** | Demostración práctica en tiempo real ejecutando las **6 pruebas obligatorias** de resiliencia, seguridad, Kafka, Saga, observabilidad y Angular. | Los 4 integrantes |
| **4. Preguntas Teóricas (Cátedra)** | **5 min** | Ronda individual de preguntas conceptuales y de diseño para calificar la competencia **CE023-N3**. | Jurado Evaluador |

---

## 4. Guía Oficial de Demostración Técnica en Vivo (Las 6 Pruebas Obligatorias)

Cada prueba cuenta con comandos reproducibles, evidencias visuales y criterios de éxito verificables:

### Prueba 1: Caída Forzada de Microservicio y Resiliencia con Fallback (Resilience4j + Feign)

* **Objetivo:** Demostrar que ante la indisponibilidad total de un microservicio dependiente (`inventario-ms`), el sistema no colapsa en cascada ni arroja errores genéricos 500, sino que ejecuta una degradación elegante controlada por **Resilience4j Circuit Breaker**.
* **Comando para provocar la caída:**
  ```bash
  docker stop medizano-inventario-ms
  ```
* **Petición ejecutada desde el API Gateway:**
  ```bash
  curl -i -X POST http://54.226.25.238:8090/api/v1/ordenes \
    -H "Authorization: Bearer <TOKEN_VALIDO>" \
    -H "Content-Type: application/json" \
    -d '{
      "clienteId": 1,
      "items": [{"productoId": 1, "cantidad": 2, "precioUnitario": 12.50}],
      "metodoPago": "EFECTIVO"
    }'
  ```
* **Resultado Obtenido (Fallback Controlado):**
  ```json
  HTTP/1.1 503 Service Unavailable
  Content-Type: application/json

  {
    "timestamp": "2026-10-30T02:45:10Z",
    "status": 503,
    "error": "Service Unavailable",
    "message": "Fallback: El servicio de inventario no se encuentra disponible temporalmente. La orden no pudo reservar stock pero fue resguardada de forma segura.",
    "path": "/api/v1/ordenes"
  }
  ```
* **Restauración del servicio:**
  ```bash
  docker start medizano-inventario-ms
  ```

---

### Prueba 2: Seguridad Perimetral y Autonomía de Resource Server (JWT)

* **Objetivo:** Demostrar el modelo de **Zero Trust**. Tanto el API Gateway como cada microservicio individual operan como un **OAuth2 Resource Server** autónomo capaz de validar la firma del token JWT sin depender de llamadas síncronas a `usuario-ms`.
* **Escenario A: Petición al Gateway sin cabecera de autenticación:**
  ```bash
  curl -i -X GET http://54.226.25.238:8090/api/v1/inventario
  # Respuesta: HTTP/1.1 401 Unauthorized
  ```
* **Escenario B: Intento de evasión (Bypass) directo al puerto interno del microservicio:**
  ```bash
  curl -i -X GET http://54.226.25.238:8085/api/v1/inventario
  # Respuesta: HTTP/1.1 401 Unauthorized (Spring Security bloquea la petición directa)
  ```
* **Escenario C: Petición con Token JWT legítimo generado en Login:**
  ```bash
  curl -i -X GET http://54.226.25.238:8090/api/v1/inventario \
    -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  # Respuesta: HTTP/1.1 200 OK + Listado de inventario
  ```

---

### Prueba 3: Mensajería Asíncrona en Event-Driven Architecture (Apache Kafka KRaft)

* **Objetivo:** Demostrar el desacoplamiento temporal de la plataforma. Al generarse una orden, los microservicios colaboradores (`pago-ms`, `facturacion-ms`) se comunican mediante eventos asíncronos sin bloquear el hilo HTTP de respuesta al cajero.
* **Tópicos Activos en el Cluster Kafka:**
  * `orden-eventos` (Particiones: 4 | Eventos: `orden.creada`, `orden.cancelada`)
  * `pago-eventos` (Particiones: 4 | Eventos: `pago.validado`, `pago.fallido`)
  * `inventario-eventos` (Particiones: 4 | Eventos: `stock.reservado`, `stock.liberado`)
* **Verificación en Kafbat UI (`http://54.226.25.238:18085/`):**
  * Se comprueba la existencia de las 12 particiones totales en el cluster KRaft.
  * Se visualiza la cola de mensajes en tiempo real, observando el payload JSON del evento `orden.creada` con su respectivo `orderId`, `montoTotal` y `traceId`.

---

### Prueba 4: Consistencia Distribuida y Transacción Compensatoria (Saga Coreografiada)

* **Objetivo:** Garantizar la consistencia eventual entre microservicios con bases de datos aisladas ante un fallo de pago imprevisto.
* **Escenario de Compensación:**
  1. Se registra una orden de venta. `inventario-ms` descuenta el stock provisionalmente.
  2. `pago-ms` procesa la transacción simulando una tarjeta denegada o sin fondos.
  3. `pago-ms` emite el evento `pago.fallido` al tópico `pago-eventos`.
  4. `orden-ms` intercepta `pago.fallido`, actualiza el estado de la orden a `CANCELADA` y publica la orden de anulación.
  5. `inventario-ms` consume el evento y ejecuta la **transacción compensatoria**: el stock previamente reservado se reintegra al lote correspondiente, manteniendo la integridad del inventario.
* **Garantía de Idempotencia:**
  * Si la pasarela de pagos reenvía la petición con la misma `idempotencyKey`, `pago-ms` responde con la transacción original almacenada en base de datos sin generar un cobro duplicado.

---

### Prueba 5: Observabilidad Centralizada y Trazabilidad Distribuida (`traceId`)

* **Objetivo:** Monitorear métricas del JVM y realizar diagnóstico forense de trazas distribuidas sin acceder a consolas de terminal.
* **Herramientas Verificadas:**
  * **Prometheus (`:9090`):** Recolección en tiempo real de métricas Actuator con sondeo cada 5s. Targets 100% en estado UP.
  * **Grafana Loki (`:3100`):** Ingesta centralizada de logs desde todos los contenedores Docker mediante Alloy.
  * **Grafana (`:3000`):** En el panel *Explore*, se filtra por `{app="orden-ms"} |= "traceId"` para comprobar que el identificador generado al inicio de la venta acompaña cada línea de bitácora en `gateway`, `orden-ms`, `inventario-ms` y `pago-ms`.

---

### Prueba 6: Demostración Funcional End-to-End desde Frontend Angular 17

* **Objetivo:** Ejecutar la venta completa en la interfaz web de producción (`https://54.226.25.238/`).
* **Flujo Operativo:**
  1. Inicio de sesión con credenciales de farmacéutico en `#/auth/login`.
  2. Apertura del Terminal POS en `#/billing`.
  3. Búsqueda y agregado de medicamentos al carrito de compras.
  4. Identificación del cliente mediante documento DNI/RUC validado con `cliente-ms`.
  5. Pago síncrono, cálculo automático del IGV (18%) y confirmación de la venta.
  6. Navegación a `#/inventory` para corroborar visualmente la rebaja de stock físico y la actualización del semáforo de existencias.

---

## 5. Matriz de Evidencias Reales del Sistema en Producción

Todas las evidencias documentadas corresponden a capturas directas tomadas con **Google Chrome** sobre la infraestructura real desplegada en AWS:

### 5.1. Registro Activo de Microservicios en Eureka Server (`:8761`)
Los 8 microservicios del sistema registrados dinámicamente y reportando heartbeat constante:

![Eureka Discovery con Microservicios en Ejecución](../assets/images/s06-eureka-chrome.png)

---

### 5.2. Catálogo Swagger UI Unificado a través del API Gateway (`:8090`)
Centralización de la documentación interactiva OpenAPI/Swagger de los microservicios:

![Swagger UI en el API Gateway](../assets/images/s06-swagger-chrome.png)

---

### 5.3. Cluster Apache Kafka KRaft en Kafbat UI (`:18085`)
Visualización del cluster de mensajería con 12 particiones y monitoreo de tópicos en tiempo real:

![Kafbat UI Cluster Apache Kafka](../assets/images/s10-kafka-ui.png)

---

### 5.4. Prometheus Targets en Estado UP (`:9090/targets`)
Inspección de endpoints de scraping de Actuator de todos los microservicios:

![Prometheus Targets UP](../assets/images/s10-prometheus-targets.png)

---

### 5.5. Métricas JVM Heap en Graficador PromQL de Prometheus
Seguimiento del consumo de memoria Heap en tiempo real por cada servicio:

![Prometheus Heap Memory Graph](../assets/images/s10-prometheus-graph.png)

---

### 5.6. Terminal POS en Angular 17 en Producción (`https://54.226.25.238/#/billing`)
Interfaz de facturación y despacho rápido de medicamentos:

![Terminal POS Angular](../assets/images/s11-angular-pos-ventas.png)

---

### 5.7. Control de Stock y Lotes FEFO en Angular (`#/inventory`)
Monitoreo de existencias farmacéuticas bajo normativa DIGEMID:

![Inventario FEFO Angular](../assets/images/s11-angular-inventario.png)

---

### 5.8. Auditoría y Gestión de Usuarios RBAC (`#/user-management`)
Panel de administración de roles y perfiles corporativos:

![Administración de Usuarios](../assets/images/s11-angular-usuarios.png)

---

## 6. Asignación Individual de Responsabilidad Técnica (Defensa de los 4 Integrantes)

Cada integrante del equipo defiende ante el jurado **2 microservicios asignados (1 transaccional y 1 no transaccional)**, demostrando autoría de código, justificación de arquitectura y dominio de las tecnologías implementadas:

| N° | Integrante | Microservicio Transaccional | Microservicio No Transaccional | Código Clave y Puntos Fuertes a Defender |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **Aldo Calla** | **`orden-ms`** (`:8082`)<br>• Órdenes y detalles de venta POS | **`catalogo-ms`** (`:8081`)<br>• Medicamentos, precios y GTIN | • Orquestación síncrona con OpenFeign.<br>• Definición de Circuit Breaker con Resilience4j.<br>• Inicio de la Saga mediante publicación en `orden-eventos`. |
| **2** | **Kenyi Calsin** | **`inventario-ms`** (`:8085`)<br>• Kardex, lotes y rotación FEFO | **`cliente-ms`** (`:8084`)<br>• Directorio de clientes y DNI/RUC | • Algoritmo de despacho FEFO (*First Expired, First Out*).<br>• Transacción compensatoria para reponer stock cancelado.<br>• Validación de documentos de identidad con `cliente-ms`. |
| **3** | **Igarlos Ruben** | **`proveedor-ms`** (`:8088`)<br>• Compras y recepción de lotes | **`facturacion-ms`** (`:8086`)<br>• Comprobantes SUNAT y correo | • Abastecimiento de stock y órdenes de compra.<br>• Cálculo de impuestos (IGV 18%) y correlativos de boleta/factura.<br>• Envío asíncrono de correo digital con SpaceMail SMTP. |
| **4** | **Diego Chambilla** | **`pago-ms`** (`:8083`)<br>• Cobros, pasarelas e idempotencia | **`usuario-ms`** (`:8087`)<br>• Roles RBAC, JWT y sesiones | • Implementación de filtro de idempotencia con tokens UUID.<br>• Generación, firmado y expiración de tokens JWT con BCrypt.<br>• Emisión de eventos `pago.validado` y `pago.fallido`. |

---

## 7. Banco de Preguntas Teóricas para la Competencia CE023-N3

Preguntas clave con respuestas técnicas rigurosas para la fase individual de preguntas de la cátedra:

* **1. ¿Cómo maneja Resilience4j la transición entre los estados `CLOSED`, `OPEN` y `HALF-OPEN`?**  
  * *Respuesta:* Inicialmente el circuito está `CLOSED` (las peticiones fluyen con normalidad). Si la tasa de fallos supera el umbral configurado (ej. 50% de fallos en una ventana deslizante de 10 peticiones), transita a `OPEN`, interrumpiendo el paso de peticiones inmediatamente y ejecutando el método de `fallback`. Transcurrido un tiempo de espera (*wait-duration-in-open-state*, ej. 10s), el circuito pasa a `HALF-OPEN`, permitiendo pasar un número controlado de peticiones de prueba. Si estas tienen éxito, vuelve a `CLOSED`; si alguna falla, regresa a `OPEN`. Esto previene el agotamiento de hilos del cliente (*thread starvation*) y evita el efecto avalancha en la red.

* **2. ¿Por qué se optó por una Saga Coreografiada con Apache Kafka y no una Saga Orquestada con un motor como Camunda?**  
  * *Respuesta:* Para el tamaño y naturaleza de MediZano (8 microservicios), la coreografía elimina el punto único de fallo (*SPOF*) y el acoplamiento que representaría un servicio orquestador centralizado. Cada microservicio reacciona de forma autónoma a los eventos de dominio de su interés (`orden.creada`, `pago.fallido`), logrando un mayor rendimiento y facilitando el escalado horizontal independiente.

* **3. ¿Cómo garantiza `pago-ms` que un pago no se procese por duplicado ante una falla de red del cajero (Idempotencia)?**  
  * *Respuesta:* El frontend genera un encabezado `Idempotency-Key` (UUIDv4) antes de enviar la solicitud. `pago-ms` intercepta la clave y busca en su base de datos si ya existe un registro con dicha llave. Si existe, retorna inmediatamente la respuesta almacenada con código HTTP 200 sin volver a interactuar con la pasarela ni emitir un segundo cobro. Si no existe, procesa el pago y persiste la llave junto al resultado dentro de la misma transacción.

* **4. ¿Por qué la autenticación JWT en cada microservicio es sin estado (*stateless*) y qué ventaja tiene sobre el manejo de sesiones en Redis?**  
  * *Respuesta:* Al utilizar JWT, la información de identidad y roles del usuario viaja contenida y firmada criptográficamente en el payload del token. Cada microservicio actúa como un *Resource Server* autónomo: valida la firma con la clave compartida sin necesidad de realizar consultas a una base de datos central o caché de sesiones distribuida (como Redis), reduciendo drásticamente la latencia de red y eliminando dependencias de infraestructura para la autorización.

* **5. ¿Qué papel cumple Micrometer Tracing y cómo viaja el `traceId` a través de Kafka y llamadas Feign?**  
  * *Respuesta:* Micrometer Tracing inyecta identificadores estandarizados según la especificación W3C Trace Context (`traceparent`). En llamadas síncronas HTTP Feign, el `traceId` se propaga automáticamente en los encabezados HTTP. En mensajería asíncrona Kafka, se transporta dentro de los *Kafka Record Headers*, lo que permite a Grafana Loki y Prometheus reconstruir el árbol de ejecución de una transacción a lo largo de múltiples servicios desacoplados.

* **6. ¿Por qué Nginx es fundamental como Reverse Proxy en producción y cómo resuelve los bloqueos de CORS?**  
  * *Respuesta:* Nginx actúa como fachada unificada en el puerto `:80`/`:443`. Sirve los archivos estáticos de la SPA y reenvía las peticiones a `/api/**` internamente al API Gateway (`:8090`). Para el navegador del cliente, tanto la interfaz como las APIs residen bajo el mismo origen (`https://54.226.25.238`), eliminando por diseño la necesidad de pre-vuelos HTTP OPTIONS y contingencias por políticas de CORS entre dominios.

---

## 8. Rúbrica Oficial de Evaluación de la Unidad 2 (Escala 0 – 20)

| Criterio de Evaluación | Ponderación | Nivel Excelente (4 pts) | Nivel Bueno (3 pts) | Nivel Regular (2 pts) | Nivel Deficiente (0–1 pt) |
| :--- | :---: | :--- | :--- | :--- | :--- |
| **1. Resiliencia y Comunicación Síncrona** | **20% (4 pts)** | Circuit Breaker y Fallback operan perfectamente ante caída forzada de microservicio; clientes Feign bien desacoplados. | Circuit Breaker configurado pero el fallback entrega datos incompletos. | Solo se implementó reintentos sin Circuit Breaker. | El sistema arroja error 500 no controlado ante fallas de servicios. |
| **2. Seguridad Perimetral y Zero Trust** | **20% (4 pts)** | Autonomía de Resource Server comprobada; rechazo de acceso sin token tanto en Gateway como en puertos internos. | Seguridad operativa en Gateway pero microservicios internos desprotegidos. | Tokens JWT sin validación de roles en endpoints críticos. | Sin autenticación o tokens sin firma verificable. |
| **3. Mensajería Asíncrona y Saga** | **20% (4 pts)** | Eventos publicados y consumidos en Kafka KRaft; compensación Saga ejecutada con restitución de stock demostrada en vivo. | Mensajería Kafka funcional pero la compensación requiere intervención manual. | Eventos implementados sin manejo de fallos ni idempotencia. | Sin integración con broker de eventos o inconsistencia de datos. |
| **4. Observabilidad Centralizada** | **20% (4 pts)** | Métricas en Prometheus y correlación de logs con `traceId` unificado en Grafana Loki visualizados en tiempo real. | Prometheus y Grafana operativos pero sin correlación distribuida de trazas. | Solo métricas básicas de Spring Boot Actuator sin centralización. | Sin herramientas de observabilidad configuradas. |
| **5. Integración Web y Defensa Técnica** | **20% (4 pts)** | Frontend Angular 17 consume vía Nginx/Gateway sin CORS; cada integrante demuestra dominio de sus 2 microservicios. | Frontend operativo pero con dependencias directas; defensa técnica desigual entre integrantes. | Frontend incompleto o dificultad para explicar el código asignado. | Frontend no funcional o desconocimiento de la arquitectura. |
