# S11 - Integración con Cliente Frontend (Angular 17)

## 1. Propósito
Integrar la Single Page Application (SPA) desarrollada en Angular 17 para interactuar con la plataforma de microservicios consumiendo exclusivamente a través del API Gateway.

## 2. Características del Cliente Web
* **Proxy Inverso en Nginx:** El frontend redirige `/api/**` hacia el API Gateway (`:8090`), eliminando problemas de CORS en producción y ocultando la topología interna.
* **Autenticación y Guíards:** Almacenamiento seguro del token JWT y protección de vistas con `canActivate` según roles (`ROLE_CLIENTE`, `ROLE_FARMACEUTICO`, `ROLE_ADMIN`).
* **Módulo de Venta POS:** Búsqueda rápida de medicamentos, carrito de compras y selección de métodos de pago (Mercado Pago, PayPal, Efectivo).

