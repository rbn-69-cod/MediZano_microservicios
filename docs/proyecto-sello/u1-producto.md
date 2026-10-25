# Producto de Unidad 1: Sistema Distribuido Base

**MediZano POS - Hito de Evaluación S5**

## 1. Producto Entregable
**Sistema distribuido base funcional, configurable y preparado para múltiples instancias concurrentes.**

Demuestra la construcción del servicio base de catálogo, la externalización de configuraciones según el ambiente (`dev`, `prod`), el registro dinámico en el servidor de descubrimiento, el acceso unificado mediante el API Gateway perimetral y el balanceo de carga entre instancias vivas.

---

## 2. Componentes Integrados en Unidad 1

1. **Servicio Base (`catalogo-ms`):**
   * API REST con Spring Boot 3 y Java 17/21.
   * Persistencia en PostgreSQL con migraciones versionadas (Flyway).
   * Documentación interactiva de APIs con OpenAPI v3 (Swagger UI).
   * Monitoreo y métricas con Spring Boot Actuator (`/actuator/health`, `/actuator/prometheus`).

2. **Servidor de Configuración Centralizada (`config-server`):**
   * Repositorio de configuración desacoplado (`config-repo/`).
   * Perfiles de despliegue `application-dev.yml` y `application-prod.yml`.
   * Los microservicios consumen su configuración al iniciar mediante `spring.config.import=optional:configserver:http://...`.

3. **Registro y Descubrimiento (`eureka-server`):**
   * Servidor Netflix Eureka donde cada instancia de microservicio se anuncia dinámicamente con su IP y puerto.
   * Soporte de múltiples instancias concurrentes (`catalogo-ms` en puertos `8081` y `8089`).

4. **API Gateway (`api-gateway`):**
   * Enrutamiento dinámico mediante `lb://catalogo-ms`, `lb://oorden-ms`, etc.
   * Filtros perimetrales y balanceo de carga Round-Robin.
   * Centralización de políticas CORS.

---

## 3. Evidencias Verificables de la Unidad 1

* **Dashboard de Eureka (`http://localhost:8761`):** Muestra las instancias registradas con estado `UP`.
* **Llamadas a través del Gateway (`http://localhost:8090/api/v1/medicamentos`):** Comprobación de que la petición se enruta correctamente al microservicio subyacente.
* **Balanceo de Carga en Vivo:** Realizar múltiples peticiones continuas al Gateway y verificar en los logs que se alternan las instancias de `catalogo-ms`.

