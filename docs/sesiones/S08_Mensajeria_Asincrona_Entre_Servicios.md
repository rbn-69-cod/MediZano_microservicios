# S08 - Mensajería Asíncrona entre Microservicios (Apache Kafka en Modo KRaft)

> **Sesión S08 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Componente:** `medizano-kafka` (Puerto `:19092`) & `medizano-kafka-ui` (Puerto `:18085`)  
> **Responsable:** Igarlos Mamani (Microservicio Transaccional / Mensajería)

---

## 1. Propósito Arquitectónico

En un flujo de venta en farmacia, cuando un cliente paga su compra, ocurren múltiples procesos secundarios:
1. Descontar las unidades vendidas del lote correspondiente en el inventario Kardex.
2. Generar el comprobante electrónico (Boleta/Factura) y enviarlo a SUNAT.
3. Actualizar el saldo contable en los reportes de cierre de caja.
4. Notificar al paciente por WhatsApp o correo electrónico.

Si todos estos pasos se hicieran de forma síncrona (HTTP REST en cadena), la petición tardaría más de 5 a 8 segundos en caja, y si el servicio de facturación estuviera caído, la venta se cancelaría injustamente.

**Apache Kafka** introduce una **Arquitectura Orientada a Eventos (EDA)**:
* `orden-ms` y `pago-ms` publican el evento y responden al cajero de inmediato.
* Los servicios interesados (`inventario-ms`, `facturacion-ms`, `reportes`) consumen el evento de forma asíncrona a su propio ritmo.

---

## 2. Mapa de Tópicos Oficiales de MediZano POS

El broker Kafka corre en modo KRaft (sin dependencia de Zookeeper) y cuenta con **4 tópicos particionados**:

| Tópico | Particiones | Factor de Réplica | Microservicio Productor | Microservicios Consumidores |
| :--- | :---: | :---: | :--- | :--- |
| **`orden-eventos`** | 3 | 1 | `orden-ms` | `pago-ms` (para iniciar cobro) |
| **`pago-eventos`** | 3 | 1 | `pago-ms` | `orden-ms` (actualiza estado) y `facturacion-ms` (emite boleta) |
| **`inventario-eventos`** | 3 | 1 | `inventario-ms` | `reporte-ms` (auditoría de mermas y Kardex) |
| **`factura-eventos`** | 3 | 1 | `facturacion-ms` | `notificacion-ms` (envío al cliente) |

---

## 3. Implementación del Productor en Spring Boot (`KafkaProducer`)

```java
package com.medizano.orden.kafka;

import com.medizano.orden.dto.OrdenEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrdenEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "orden-eventos";

    public void publicarOrdenCreada(OrdenEventDTO evento) {
        log.info("Publicando evento ORDEN_CREADA al topico {} con Key: {}", TOPIC, evento.getOrdenId());
        // Se usa el ordenId como Key para garantizar que todos los eventos de la misma orden vayan a la misma particion
        kafkaTemplate.send(TOPIC, String.valueOf(evento.getOrdenId()), evento);
    }
}
```

---

## 4. Implementación del Consumidor con Idempotencia (`KafkaConsumer`)

```java
package com.medizano.pago.kafka;

import com.medizano.pago.dto.OrdenEventDTO;
import com.medizano.pago.service.PagoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrdenEventConsumer {

    private final PagoService pagoService;

    @KafkaListener(topics = "orden-eventos", groupId = "pago-service-group")
    public void procesarOrdenCreada(OrdenEventDTO evento) {
        log.info("Evento recibido en pago-ms: Orden #{} por un total de S/ {}", evento.getNumeroOrden(), evento.getTotal());
        try {
            // El servicio procesa el pago garantizando que no se duplique por reintentos
            pagoService.procesarPagoOrden(evento);
        } catch (Exception ex) {
            log.error("Error al procesar evento de orden: {}", ex.getMessage());
        }
    }
}
```

---

## 5. Inspección Gráfica en Kafbat UI (`:18085`)

1. Abrir `http://localhost:18085/` en el navegador.
2. Navegar a **Clusters $\rightarrow$ medizano-cluster $\rightarrow$ Topics**.
3. Seleccionar `pago-eventos` y abrir la pestaña **Messages** para inspeccionar la carga útil en JSON:

```json
{
  "eventoId": "EVT-PAG-001",
  "tipo": "PAGO_COMPLETADO",
  "ordenId": 101,
  "monto": 45.50,
  "metodoPago": "EFECTIVO",
  "estado": "APROBADO",
  "fecha": "2026-10-25T17:10:00"
}
```
