# S10 - Observabilidad Centralizada y Diagnóstico Distribuido

> **Sesión S10 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Herramientas:** Prometheus (:9090), Grafana (:3000), Grafana Loki (:3100), Grafana Alloy (:12345)

---

## 1. Los Tres Pilares de la Observabilidad en MediZano

En una arquitectura de 8 microservicios con múltiples instancias corriendo en contenedores, conectarse a cada máquina o contenedor por SSH para leer archivos `.log` es inviable.

MediZano implementa la **Tríada de Observabilidad Cloud-Native**:

```text
               +---------------------------------------+
               |          GRAFANA (:3000)             |
               | (Visualización y Dashboards Unificados)|
               +-------------------+-------------------+
                                   |
         +-------------------------+-------------------------+
         |                                                   |
         v                                                   v
   [ MÉTRICAS ]                                          [ LOGS ]
  Prometheus (:9090)                                   Loki (:3100)
         ^                                                   ^
         | Scrape HTTP (/actuator/prometheus)                | Docker Daemon Logs
+--------+--------+                                 +--------+--------+
| Spring Boot MS  |                                 | Grafana Alloy   |
+-----------------+                                 +-----------------+
```

1. **Métricas (Prometheus):** Cantidad de peticiones por segundo (RPS), latencia percentil 95 (P95), consumo de memoria Heap JVM y conexiones activas a PostgreSQL.
2. **Logs Agregados (Loki):** Ingesta continua de todas las salidas estándar de los contenedores Docker sin indexar el texto completo, lo que ahorra 90% de RAM frente a Elasticsearch.
3. **Dashboards (Grafana):** Tableros pre-aprovisionados que combinan métricas y logs en una sola pantalla.

---

## 2. Configuración de Prometheus (`obs/prometheus/prometheus.yml`)

Prometheus sondea los endpoints Actuator de los microservicios cada 5 segundos:

```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway:8090']

  - job_name: 'catalogo-ms'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['catalogo-ms:8081']

  - job_name: 'oorden-ms'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['oorden-ms:8082']

  - job_name: 'inventario-ms'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['inventario-ms:8085']

  - job_name: 'pago-ms'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['pago-ms:8083']
```

---

## 3. Correlación de Trazas mediante `TraceId` en Logs (MDC)

Para rastrear una petición que viaja desde el Frontend $\rightarrow$ Gateway $\rightarrow$ `oorden-ms` $\rightarrow$ `inventario-ms`, cada microservicio inyecta un identificador único de correlación en el patrón de log de Spring Boot:

```properties
# Pattern de log en application.properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [traceId=%X{traceId}] %logger{36} - %msg%n
```

### Consulta en Grafana / Loki (LogQL):
En Grafana Explore (`http://localhost:3000/explore`), el jurado puede filtrar todos los logs generados por una venta con una sola consulta:

```logql
{container_name=~"medizano-.*"} |= "traceId=a8f93e12c4b0"
```

Esto muestra la secuencia completa en oorden cronológico:
1. `[api-gateway] Enrutando POST /api/v1/oóordenes`
2. `[oorden-ms] Creando oorden #ORD-101 para cliente 1`
3. `[inventario-ms] Descontando 2 unidades de Lote LOT-PRV-2026-01`
4. `[pago-ms] Pago procesado con éxito por S/ 45.50`

---

## 4. Dashboards Preconfigurados en Grafana

Al ingresar a `http://localhost:3000` con `admin` / `admin`, el sistema ya tiene cargados:
* **JVM Micrometer Dashboard:** Memoria Garbage Collector, hilos y CPU de cada microservicio.
* **HTTP Traffic & Latency:** Gráfico en tiempo real de códigos 200 OK vs 4xx/5xx y tiempos de respuesta del Gateway.
