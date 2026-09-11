# Auditoría integral de pagos de MediZano

Fecha de corte: 8 de septiembre de 2026, 23:02 (America/Lima)

Alcance: Checkout Pro de Mercado Pago, PayPal Checkout, POS, órdenes, inventario, facturación, devoluciones, reportes, seguridad, despliegue y respaldo.

Método: revisión de código y configuración, pruebas locales, consultas de solo lectura al VPS y a las API de los proveedores, revisión de logs y contraste con documentación oficial. No se realizaron nuevos cobros ni se modificaron datos productivos durante la auditoría.

## Dictamen

**NO-GO para pagos reales por ahora.** El camino feliz sí funciona: el sistema creó y confirmó pagos, marcó órdenes como pagadas, descontó inventario y generó comprobantes. Sin embargo, hay cuatro bloqueadores de producción:

1. No existe un flujo monetario de reembolso ni gestión de contracargos para pagos electrónicos.
2. Las ventas electrónicas no aparecen en historial, reportes ni devoluciones.
3. Las notificaciones de proveedores no están completas ni probadas de extremo a extremo.
4. El respaldo diario programado falla por permisos.

Hasta corregirlos, Mercado Pago debe permanecer con sus credenciales de prueba actuales. PayPal continúa correctamente en sandbox y no debe pasar a Live.

## Estado observado

### Infraestructura

- Los 12 contenedores del VPS estaban activos; los servicios con healthcheck reportaron `healthy`.
- `pago-ms` se ejecuta como usuario no privilegiado `medizano` y tiene política de reinicio `always`.
- El acceso externo usa HTTPS y los secretos están en variables del VPS; no se encontraron credenciales reales versionadas en Git.
- PayPal apunta a `api-m.sandbox.paypal.com`.
- Mercado Pago apunta a `api.mercadopago.com`; este host sirve tanto pruebas como producción, por lo que el entorno lo define la credencial, no el hostname ni el texto “sandbox” de una URL.

### Datos de pagos

| Proveedor | Estado local | Orden confirmada | Cantidad |
|---|---:|---:|---:|
| Mercado Pago | APPROVED | Sí | 1 |
| Mercado Pago | PENDING | No | 4 |
| PayPal | APPROVED | Sí | 2 |
| PayPal | PENDING | No | 3 |

- No hay actualmente identificadores externos duplicados.
- No hay órdenes pagadas pendientes de inventario o factura.
- No hay facturas duplicadas por orden en los datos actuales.
- El pago de Mercado Pago aprobado consultado directamente al proveedor devolvió `live_mode=true`, `approved/accredited`, S/5 y la referencia de la orden 10. Una verificación posterior confirmó que el Access Token pertenece a un vendedor `TESTUSER`, etiquetado `test_user`, con correo `@testuser.com`, y que ese mismo usuario es el `collector_id` del pago. **Fue una transacción de prueba sin movimiento real.** En este flujo el campo `live_mode` aislado no es prueba suficiente de movimiento productivo.
- Las dos órdenes PayPal aprobadas están en sandbox. Una captura quedó temporalmente retenida para revisión y posteriormente terminó `COMPLETED`; el reintento idempotente funcionó.

### Pruebas ejecutadas

- Órdenes: 4/4.
- Inventario: 4/4.
- Pago: 12/12 (5 Mercado Pago y 7 PayPal).
- Facturación POS: 3/3.
- Frontend: lint correcto y build de producción correcto.
- Dependencias de producción del frontend: `npm audit --omit=dev` sin vulnerabilidades conocidas en el corte de la auditoría.

Estas pruebas son unitarias con mocks. No cubren webhooks reales, eventos fuera de orden, concurrencia, caídas entre microservicios, reembolsos, contracargos, restauración de respaldo ni transición de sandbox a producción.

## Hallazgos

### P0 — Bloqueadores inmediatos

#### P0.1 Los “reembolsos” no devuelven dinero al cliente

`ReturnService` registra una devolución local, cambia `Bill.paymentStatus` y repone inventario (`ReturnService.java`, líneas 44–144), pero no llama a PayPal ni a Mercado Pago. Además, solo acepta un `Bill`; los pagos electrónicos generan una `Factura` en otro modelo.

PayPal expone el reembolso sobre la captura y Mercado Pago permite reembolsos totales o parciales mediante sus API ([idempotencia y enlace de refund de PayPal](https://developer.paypal.com/reference/guidelines/idempotency/), [reembolsos y cancelaciones de Mercado Pago](https://www.mercadopago.com.pe/developers/es/docs/checkout-api-payments/payment-management/cancellations-and-refunds)).

Impacto: el sistema puede decir “REFUNDED” y reponer stock mientras el comprador conserva el cargo; también puede existir un reembolso en el proveedor que MediZano desconozca.

Acción requerida: crear un flujo de reembolso electrónico idempotente, persistir identificador/importe/estado del refund, consultar al proveedor, emitir nota de crédito y reponer inventario solo bajo la política aprobada. Debe soportar reembolso parcial, total, fallo y reintento.

#### P0.2 Contabilidad fragmentada: pagos electrónicos invisibles

El VPS contiene 3 `facturas` electrónicas por S/25, pero `bills=0` y `bill_payments=0`. `ReportingService` calcula ventas y caja exclusivamente desde `Bill` y sus `Payment` (`ReportingService.java`, líneas 44–123). El historial también llama solamente a `/cashier/bills` (`purchase-history.component.ts`, líneas 27–39), y devoluciones requieren `billId`.

Impacto: reportes de ventas/caja en cero pese a pagos electrónicos confirmados, historial incompleto, IGV inconsistente y ausencia de devoluciones para operaciones electrónicas.

Acción requerida: definir una sola fuente contable de ventas. La opción más segura es que toda orden confirmada cree el mismo agregado de comprobante/pago que consumen historial, reportes y devoluciones, o que estos módulos consoliden explícitamente `Bill` y `Factura` sin doble conteo. Añadir conciliación diaria proveedor → pago → orden → comprobante → inventario.

#### P0.3 Webhooks incompletos

Mercado Pago:

- La preferencia más reciente devuelve `notification_url=null`, `auto_return=''` y `expires=false`.
- El único log es una prueba HMAC controlada de esta auditoría; todavía falta ejecutar la simulación oficial desde el panel. La ausencia de una notificación por la compra de prueba es esperable: Mercado Pago indica que los pagos hechos con credenciales de prueba no envían notificaciones y que el receptor debe validarse mediante **Simular** en Tus integraciones ([notificaciones de Checkout Pro](https://www.mercadopago.com.pe/developers/en/docs/checkout-pro-preferences/payment-notifications)).
- El controlador obtiene `data.id` del cuerpo, aunque el ejemplo oficial lo lee del parámetro de consulta `data.id` (`PagoController.java`, líneas 148–168).
- El handler verifica, consulta al proveedor, persiste y confirma orden/inventario/factura de forma síncrona antes de responder. Mercado Pago solicita responder 200/201 dentro de 22 segundos y reintenta cuando no recibe confirmación ([Webhooks de Mercado Pago](https://www.mercadopago.com.pe/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks)).
- Estados distintos de `approved`, `rejected` o `cancelled` vuelven a `PENDING`; un `refunded` o contracargo no se representa correctamente (`MercadoPagoPaymentService.java`, líneas 289–301).

PayPal:

- No hay endpoint, Webhook ID, validación de firma ni procesador de eventos.
- PayPal recomienda suscribir un endpoint HTTPS, verificar la firma y responder 2xx; reintenta entregas fallidas hasta 25 veces durante 3 días ([integración de webhooks](https://developer.paypal.com/api/rest/webhooks/rest/), [visión general](https://developer.paypal.com/api/rest/webhooks)).

Impacto: pagos aprobados, reembolsados o revertidos fuera de la ventana del navegador pueden quedar sin reflejarse; se depende de que el cajero mantenga el modal abierto.

Acción requerida: tabla duradera de eventos con ID único, verificación de firma sobre la entrada original, respuesta rápida y procesamiento asíncrono idempotente. Suscribir pagos aprobados/pendientes/rechazados, capturas completadas/pendientes/denegadas, reembolsos, reversos y contracargos. Mantener reconciliación programada como red de seguridad.

#### P0.4 El respaldo diario programado no funciona

Cron ejecuta `/opt/medizano/deploy/backup.sh` a las 03:15, pero el archivo remoto tiene permisos `0644`; el log registra `Permission denied`. Hay un único archivo del 7 de septiembre, válido a nivel gzip, pero el cron no ha producido ninguno y no existe evidencia de restauración probada.

Impacto: pérdida irreversible de pagos, órdenes, comprobantes y trazabilidad ante daño de disco o error operativo.

Acción requerida: hacer ejecutable el script o invocarlo con `/bin/bash`, ejecutar un backup inmediato, alertar cuando falte el backup diario, copiarlo fuera del VPS y probar restauración en una base aislada. Un gzip válido no demuestra que la restauración sea utilizable.

### P1 — Riesgos altos

#### P1.1 Flujo PayPal frágil y dependiente del cajero

Después de abrir PayPal, el sistema pregunta manualmente al cajero si el cliente autorizó y, al aceptar, intenta capturar. La URL de retorno construida con `/#/billing` fue normalizada por PayPal a `/billing/?...` y produjo la ventana blanca observada. El SDK de JavaScript está cargado, pero no gobierna el flujo.

El patrón oficial usa botones/SDK, `createOrder` en backend y captura backend desde `onApprove`; además distingue errores recuperables como `INSTRUMENT_DECLINED` ([integración estándar de PayPal](https://developer.paypal.com/platforms/checkout/standard/integrate)).

Acción: usar el SDK de PayPal con callbacks `createOrder`, `onApprove`, `onCancel` y `onError`; o crear una ruta real sin hash que procese el retorno. Nunca pedir al empleado que confirme un hecho que debe confirmar el proveedor.

#### P1.2 Falta de restricciones únicas y control de concurrencia

La tabla `pagos` solo tiene su clave primaria; los IDs de orden/captura/preferencia/pago externos no son únicos. La tabla `facturas` solo hace único `numero_factura`, no `orden_id` (`Factura.java`, líneas 26–32). Los chequeos “buscar y luego insertar” pueden competir bajo solicitudes concurrentes.

Hay defensas parciales positivas: `Pago` usa `@Version`, PayPal envía `PayPal-Request-Id`, Mercado Pago envía `X-Idempotency-Key`, y las operaciones de inventario poseen una restricción única. Esas defensas no sustituyen constraints en pagos y facturas.

Acción: migraciones con índices únicos parciales/convencionales para IDs externos y una factura por orden; bloqueo o compare-and-set para transiciones de estado; pruebas concurrentes.

#### P1.3 Sin migraciones reproducibles

Todos los microservicios usan `spring.jpa.hibernate.ddl-auto=update` y no existe Flyway/Liquibase. Hibernate no es una estrategia segura de evolución de esquemas financieros ni permite auditar exactamente qué cambió.

Acción: adoptar migraciones versionadas, establecer `ddl-auto=validate` en producción, respaldar antes de cada migración y probar avance/rollback operativo.

#### P1.4 Clientes HTTP sin límites ni aislamiento

`MercadoPagoPaymentService` crea `new RestTemplate()` y `PayPalService` crea un `RestClient` básico, sin timeouts explícitos, circuit breaker ni política de retry. Las llamadas externas se realizan dentro de transacciones de base de datos.

Impacto: hilos y conexiones retenidos si el proveedor tarda, transacciones largas, saturación del servicio y respuestas de webhook tardías.

Acción: timeouts de conexión/respuesta, reintentos solo idempotentes con jitter, circuit breaker, límites de concurrencia y procesamiento asíncrono/outbox.

#### P1.5 Conversión PayPal no auditable

Los montos PEN se convierten a USD con una tasa fija `3.75`. La base persiste el monto USD, pero no la suma original PEN, la tasa utilizada, su fuente ni la fecha. Los reportes actuales esperan montos homogéneos.

Impacto: descuadre contable y dificultad para explicar cuánto se cobró por una venta en soles.

Acción: persistir monto/moneda de venta, monto/moneda de liquidación, tasa, fuente, instante, comisión y neto; establecer política de redondeo y vigencia.

#### P1.6 Plataforma backend fuera de soporte

El proyecto usa Spring Boot 3.2.0. Spring anunció que 3.2.12 cerró el soporte OSS de la rama 3.2 en noviembre de 2024 y recomienda actualizar ([anuncio oficial](https://spring.io/blog/2024/11/21/spring-boot-3-2-12-available-now/)); en el corte actual, 3.4 y 3.5 son las ramas 3.x mantenidas ([versiones soportadas](https://github.com/spring-projects/spring-boot/wiki)).

Acción: actualizar primero al último patch soportado mediante una rama y suite de regresión; revisar también Spring Cloud, JJWT, springdoc e iText.

### P2 — Riesgos medios

1. **Pendientes abandonados:** hay 7 pagos locales `PENDING`; cerrar el modal deja la orden/pago sin una bandeja clara para reabrir, cancelar o expirar.
2. **Preferencias sin vencimiento:** `expires=false` permite reutilización prolongada del enlace/QR. Configurar expiración conforme al turno de caja.
3. **Sin `notification_url` por preferencia:** depender solo del panel hace más difícil aislar ambientes y probar cada despliegue.
4. **QR genérico, no QR nativo:** el QR implementado codifica el enlace de Checkout Pro y es adecuado como interfaz sin teclado. No es el producto nativo “Código QR” de Mercado Pago, que la tabla oficial no ofrece en Perú ([disponibilidad por país](https://www.mercadopago.com.pe/developers/es/docs/getting-started)).
5. **Zona horaria ambigua:** la aplicación guarda `LocalDateTime` UTC en columnas sin zona mientras PostgreSQL opera en America/Lima; se observaron edades negativas de pendientes cercanas a 5 horas.
6. **Estados incompletos en UI:** rechazo, cancelación, revisión, devolución y contracargo no tienen una representación operativa completa.
7. **Exposición en logs:** se registran cuerpos de error de proveedores y el frontend conserva algunos `console.error` con datos operativos. Sanitizar PII, tokens, enlaces y payloads.
8. **Sin rate limiting:** no se encontró limitación de tasa en gateway/Nginx para creación, captura, verificación o reconciliación.
9. **Observabilidad insuficiente:** hay Actuator/Prometheus en dependencias, pero el stack de monitorización no está activo en producción ni hay alertas por pago atascado, webhook fallido o descuadre.
10. **Contrato/documentación desalineados:** `/api/v1/pagos/config` es público en gateway, aunque su OpenAPI/README indica autenticación. Client ID/Public Key pueden ser públicos, pero el contrato debe ser consistente y no devolver `baseUrl` si no es necesario.
11. **Preferencias API vs Orders API:** el código implementa correctamente el recurso legado/soportado `/checkout/preferences`, que coincide con la aplicación seleccionada por el usuario; no explica el antiguo error 422 del panel. La documentación nueva también ofrece Checkout Pro mediante Orders API. No migrar solo por apariencia: decidir una API, documentarla y probarla completamente.
12. **Control de entorno explícito:** las credenciales actuales sí son de prueba, pero el sistema no conserva ni muestra esa verificación. No se debe deducir el entorno únicamente del prefijo `APP_USR` ni de `live_mode`; las credenciales de prueba y producción pueden compartir prefijo. Registrar un modo `TEST|LIVE`, verificar la identidad del collector al arrancar y bloquear una transición no aprobada.

## Fortalezas confirmadas

- El backend recalcula/valida la orden; no confía en un monto enviado por el navegador.
- Antes de confirmar Mercado Pago valida referencia externa, monto y moneda contra el registro local.
- PayPal valida `custom_id`, monto, moneda, Order ID y Capture ID después de capturar.
- Hay idempotencia en creación/captura y reintentos de confirmación de orden.
- Inventario registra una operación única por venta, lo que reduce dobles descuentos.
- La orden pagada conserva banderas separadas para inventario y factura, con reintentos.
- El QR se genera localmente a partir del enlace; no expone Access Token ni Secret.
- Endpoints de cobro requieren rol ADMIN/CASHIER; el endpoint interno de confirmación está bloqueado en gateway y protegido entre servicios.
- Los contenedores Java se ejecutan con usuario no root.
- En los datos presentes no se detectaron duplicados ni órdenes pagadas incompletas.

## Plan recomendado

### Antes de cualquier nueva prueba

1. Conservar las credenciales de prueba actuales y verificar al arrancar que pertenecen al vendedor `test_user`; no usar `live_mode` como único criterio.
2. Reparar el backup y sacar una copia externa verificable.

### Sprint de seguridad transaccional

1. Unificar modelo contable e incluir pagos electrónicos en historial/reportes/devoluciones.
2. Implementar refunds y eventos de reembolso/contracargo en ambos proveedores.
3. Implementar webhooks duraderos e idempotentes para Mercado Pago y PayPal.
4. Añadir control de entorno, expiración de enlaces y selector correcto `init_point`/`sandbox_init_point`.
5. Sustituir la confirmación manual de PayPal por `onApprove`.
6. Añadir migraciones, constraints, locks y ledger de eventos.
7. Configurar timeouts, retries controlados, rate limits, métricas y alertas.

### Validación obligatoria antes de Live

- Aprobado, pendiente, rechazado y cancelado en cada proveedor.
- Doble clic, dos cajeros y webhooks duplicados/fuera de orden.
- Caída de pago-ms, orden-ms, inventario-ms y facturacion-ms en cada punto del flujo.
- Reembolso parcial/total y contracargo iniciado desde el panel del proveedor.
- Reconciliación entre bruto, comisión, neto, moneda y comprobante.
- Expiración y reintento de enlace/QR.
- Rotación de credenciales y webhook secret.
- Restauración completa desde backup en un entorno aislado.
- Prueba de humo Live de importe mínimo, con plan de reverso y aprobación explícita.

## Intervenciones humanas necesarias

1. En Mercado Pago Developers se debe configurar y **simular** el webhook HTTPS de prueba, confirmando eventos seleccionados.
2. En PayPal Developer se debe crear el webhook sandbox, guardar su Webhook ID y probarlo con el simulador y una compra sandbox.
3. Contabilidad debe definir si la moneda funcional será PEN y cómo registrar la conversión/comisión de PayPal.
4. El negocio debe definir política de devolución de medicamentos antes de automatizar reposición de inventario.
5. Un responsable debe aprobar formalmente el paso TEST → LIVE después de completar la matriz anterior.

## Fuentes oficiales principales

- [Mercado Pago — Credenciales de Checkout Pro](https://www.mercadopago.com.pe/developers/en/docs/checkout-pro-preferences/resources/credentials)
- [Mercado Pago — Compras de prueba](https://www.mercadopago.com.pe/developers/es/docs/checkout-pro-preferences/integration-test/test-purchases)
- [Mercado Pago — Crear preferencia](https://www.mercadopago.com.pe/developers/es/docs/checkout-pro-preferences/create-payment-preference)
- [Mercado Pago — Webhooks](https://www.mercadopago.com.pe/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks)
- [Mercado Pago — Reembolsos y cancelaciones](https://www.mercadopago.com.pe/developers/es/docs/checkout-api-payments/payment-management/cancellations-and-refunds)
- [Mercado Pago — Disponibilidad por país](https://www.mercadopago.com.pe/developers/es/docs/getting-started)
- [PayPal — Integración estándar Checkout](https://developer.paypal.com/platforms/checkout/standard/integrate)
- [PayPal — Integración y verificación de webhooks](https://developer.paypal.com/api/rest/webhooks/rest/)
- [PayPal — Idempotencia](https://developer.paypal.com/reference/guidelines/idempotency/)
- [Spring — Fin de soporte OSS de Boot 3.2](https://spring.io/blog/2024/11/21/spring-boot-3-2-12-available-now/)
