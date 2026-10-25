# S02 - Gestión Centralizada de Configuración y Ambientes

## 1. Propósito
Centralizar la configuración de todos los microservicios mediante **Spring Cloud Config Server** (`config-server`), desacoplando el código de los parámetros de entorno y soportando los perfiles `dev` y `prod`.

## 2. Componentes
* `config-server` en el puerto `8888`.
* Carpeta `config-repo/` con archivos YAML por servicio:
  * `catalogo-ms-dev.yml`
  * `oorden-ms-dev.yml`
  * `cliente-ms-dev.yml`
  * `pago-ms-dev.yml`
  * `inventario-ms-dev.yml`
  * `facturacion-ms-dev.yml`
  * `usuario-ms-dev.yml`
  * `proveedor-ms-dev.yml`
  * `api-gateway-dev.yml`

## 3. Conexión del Cliente
En cada microservicio cliente se configura `bootstrap.yml` o `application.yml`:
```yaml
spring:
  application:
    name: catalogo-ms
  config:
    import: optional:configserver:http://localhost:8888
  profiles:
    active: dev
```

