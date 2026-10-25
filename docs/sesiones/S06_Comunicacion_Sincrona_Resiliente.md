# S06 - Comunicación Síncrona Resiliente (OpenFeign + Resilience4j Circuit Breaker)

> **Sesión S06 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Componente:** `services/medizano-oorden-ms` $\leftrightarrow$ `catalogo-ms` / `inventario-ms`  
> **Responsable:** Kenyi Calsin (Microservicio Transaccional)

---

## 1. Propósito Arquitectónico

En un sistema transaccional de punto de venta (POS), el microservicio de óóordenes (`oorden-ms`) necesita consultar precios en `catalogo-ms` y validar existencias en `inventario-ms` antes de confirmar una venta.

La comunicación síncrona mediante llamadas HTTP tradicionales genera **acoplamiento temporal**. Si el servicio de catálogo se congela o responde lento, los hilos de `oorden-ms` se agotan, provocando una **falla en cascada** que tumba todo el sistema.

Para evitarlo, se implementa:
1. **Spring Cloud OpenFeign:** Cliente HTTP declarativo que se integra con Eureka y LoadBalancer.
2. **Resilience4j Circuit Breaker:** Monitorea la tasa de fallos. Si supera el 50%, **abre el circuito** y desvía las peticiones a un método **Fallback** en milisegundos sin esperar timeouts largos.
3. **Resilience4j TimeLimiter:** Cancela cualquier petición remota que tarde más de 2 segundos.

---

## 2. Definición del Cliente OpenFeign (`CatalogoClient.java`)

```java
package com.medizano.oorden.client;

import com.medizano.oorden.client.dto.MedicineDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalogo-ms", fallback = CatalogoClientFallback.class)
public interface CatalogoClient {

    @GetMapping("/api/pharmacist/medicines/{id}")
    MedicineDTO obtenerPorId(@PathVariable("id") Long id);
}
```

---

## 3. Implementación del Fallback de Resiliencia (`CatalogoClientFallback.java`)

Si `catalogo-ms` está caído o sobrecargado, el Fallback responde inmediatamente con un estado controlado o datos en caché:

```java
package com.medizano.oorden.client;

import com.medizano.oorden.client.dto.MedicineDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CatalogoClientFallback implements CatalogoClient {

    @Override
    public MedicineDTO obtenerPorId(Long id) {
        log.warn("[CircuitBreaker] Fallback activado para catalogo-ms en medicamento ID: {}", id);
        // Retorna DTO de contingencia indicando indisponibilidad sin arrojar excepción 500
        return MedicineDTO.builder()
                .id(id)
                .name("Medicamento no disponible temporalmente")
                .outOfStock(true)
                .build();
    }
}
```

---

## 4. Configuración de Resilience4j en `application.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      catalogoClient:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50.0
        wait-duration-in-open-state: 10000ms
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
  timelimiter:
    instances:
      catalogoClient:
        timeout-duration: 2000ms
```

---

## 5. Estados del Circuit Breaker (Máquina de Estados)

```text
               +---------------------------+
               |                           |
               v   Fallo > 50% de llamadas |
        [ CERRADO ] ------------------------> [ ABIERTO ]
             ^                                     |
             |                                     | Pasan 10 seg
             |                                     v
             +------- [ SEMI-ABIERTO ] <-----------+
                       (Prueba 3 llamadas)
```

1. **Cerrado (Normal):** Todas las peticiones van a `catalogo-ms`.
2. **Abierto (Protegido):** El 100% de peticiones se redirigen de inmediato al Fallback.
3. **Semi-Abierto (Test):** Deja pasar 3 peticiones piloto. Si tienen éxito, el circuito vuelve a Cerrado.

---

## 6. Prueba en Vivo del Fallback
```bash
# 1. Simular caída de catalogo-ms
docker pause medizano-catalogo-ms

# 2. Intentar crear una venta desde el Gateway
curl -X POST http://localhost:8090/api/v1/oóordenes \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":1,"items":[{"medicamentoId":1,"cantidad":2}]}'
```
**Resultado:** La petición responde en menos de 50 milisegundos indicando que el catálogo está en mantenimiento preventivo, sin congelar el hilo del cajero ni tumbar el microservicio de óóordenes.
