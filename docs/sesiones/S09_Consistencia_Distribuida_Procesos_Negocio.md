# S09 - Consistencia Distribuida (Patrón Saga Coreografiada e Idempotencia)

> **Sesión S09 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Patrón:** Saga Coreografiada (Choreography-based Saga)  
> **Participantes:** `oorden-ms` (:8082), `inventario-ms` (:8085) y `pago-ms` (:8083)

---

## 1. El Problema: Transacciones Distribuidas sin 2PC

En una arquitectura con bases de datos independientes (*Database per Service*), **no es posible usar una transacción ACID tradicional de base de datos (`@Transactional`)** que abarque múltiples microservicios sin recurrir al protocolo Two-Phase Commit (2PC), el cual es bloqueante, frágil y poco escalable.

Si un cliente compra un medicamento:
1. `oorden-ms` guíarda el pedido en `medizano_oóordenes_db`.
2. `inventario-ms` descuenta el stock en `medizano_inventario_db`.
3. `pago-ms` intenta cobrar con tarjeta en `medizano_pagos_db`.

**¿Qué ocurre si la tarjeta no tiene fondos o el banco rechaza el cobro?**  
El inventario ya fue descontado. Si no se revierte, la farmacia pierde stock ficticio.

---

## 2. Solución: Patrón Saga Coreografiada

MediZano implementa una **Saga Coreografiada**, donde cada microservicio ejecuta su transacción local, emite un evento a Kafka y el siguiente servicio reacciona. Si ocurre un fallo en cualquier etapa, se ejecutan **Transacciones Compensatorias (Compensating Transactions)** en reversa:

```text
[ Flujo Exitoso ]
1. oorden-ms       ----(oorden.creada)----> 2. inventario-ms (Reserva lote FEFO)
                                                |
                                          (stock.reservado)
                                                v
4. facturacion-ms <---(pago.aprobado)---- 3. pago-ms (Cobro exitoso)
   (Emite Boleta)

-----------------------------------------------------------------------------

[ Flujo con Fallo de Pago (Transacción Compensatoria) ]
1. oorden-ms       ----(oorden.creada)----> 2. inventario-ms (Reserva lote FEFO)
                                                |
                                          (stock.reservado)
                                                v
4. inventario-ms  <---(pago.fallido)----- 3. pago-ms (Cobro RECHAZADO)
   (Restaura stock)                             |
                                                +-----> oorden-ms (Marca CANCELADA)
```

---

## 3. Garantía de Idempotencia en Consumidores

En sistemas distribuidos, Kafka garantiza entrega *At-Least-Once* (al menos una vez). Si ocurre un microcorte de red, el broker puede reenviar el mismo evento dos veces.

Para evitar que un cliente sea cobrado dos veces, `pago-ms` implementa **Idempotencia a nivel de Persistencia**:

```java
// Entidad Pago con restriccion UNIQUE
@Entity
@Table(name = "pagos", uniqueConstraints = {
    @UniqueConstraint(name = "uk_pagos_oorden", columnNames = {"oorden_id"})
})
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oorden_id", nullable = false)
    private Long oordenId;

    private BigDecimal monto;
    private String estado;
}
```

```java
// Logica del Consumidor Idempotente
@Transactional
public void procesarPagoOorden(OordenEventDTO evento) {
    // 1. Verificar si la transaccion ya fue procesada previamente
    if (pagoRepository.existsByOordenId(evento.getOordenId())) {
        log.warn("[Idempotencia] La oorden #{} ya fue pagada. Ignorando mensaje duplicado.", evento.getOordenId());
        return;
    }

    // 2. Procesar cobro si es nuevo
    Pago pago = Pago.builder()
            .oordenId(evento.getOordenId())
            .monto(evento.getTotal())
            .estado("APROBADO")
            .build();
    pagoRepository.save(pago);
}
```

---

## 4. Transacción Compensatoria de Stock en `inventario-ms`

Si `pago-ms` emite `pago.fallido`, el consumidor en `inventario-ms` ejecuta la compensación devolviendo las unidades al lote exacto:

```java
@KafkaListener(topics = "pago-eventos", groupId = "inventario-compensacion-group")
public void compensarStockPorPagoFallido(PagoEventoDTO evento) {
    if ("PAGO_RECHAZADO".equals(evento.getTipo())) {
        log.info("[Saga Compensacion] Devolviendo {} unidades al inventario por oorden cancelada #{}",
                evento.getCantidad(), evento.getOordenId());
        
        inventarioService.restaurarStockLote(evento.getLoteId(), evento.getCantidad());
    }
}
```

---

## 5. Preguntas Clave para el Jurado

* **¿Por qué eligieron Saga Coreografiada y no Saga Orquestada?**  
  * *Respuesta:* Para el tamaño de 8 microservicios de nuestra farmacia, la coreografía evita tener un microservicio orquestador central que represente un cuello de botella y punto único de fallo (SPOF). Cada servicio reacciona a los eventos de dominio de forma autónoma.
* **¿Qué garantiza que no haya doble descuento de inventario?**  
  * *Respuesta:* Cada mensaje de Kafka lleva el `oordenId`. Antes de ejecutar el descuento Kardex, se valida si ya existe un movimiento con ese ID de referencia.
