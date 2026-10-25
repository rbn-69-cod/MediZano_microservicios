# GuÃ­a del Proyecto Sello: MediZano POS

## 1. Propósito
El Proyecto Sello integra todas las capacidades del curso de **Desarrollo de Aplicaciones Distribuidas** alrededor del sistema farmacéutico y de punto de venta **MediZano POS**, construido de forma progresiva. Cada sesión añade una capacidad arquitectónica distribuida hasta llegar a un sistema empresarial end-to-end configurable, seguro, tolerante a fallos, consistente, observable y defendible técnicamente.

### Competencia Principal (ICACIT)
* **CE023 (Nivel 3 - Programación):** Diseña, implementa y defiende sistemas de software distribuidos escalables, seguros e interoperables.
* **CE022 (Ingeniería de la Información):** Diseña modelos de persistencia políglota, eventos asíncronos y consistencia distribuida.
* **CE024 (Calidad de Software):** Implementa resiliencia ante fallos, observabilidad integral, trazabilidad distribuida y despliegues reproducibles.

---

## 2. Visión del Sistema
```text
Servicio Base -> Config Server -> Eureka Discovery -> API Gateway -> Seguridad JWT/RBAC ->
Resiliencia Feign -> Eventos Kafka -> Consistencia Saga -> Observabilidad (Prometheus/Loki/Grafana) ->
Frontend Angular -> SustentaciÃ³n TÃ©cnica
```

MediZano POS resuelve la gestión integral de farmacias y boticas:
* Venta Ã¡gil en mostrador (POS) y en lÃ­nea.
* Control estricto de lotes de medicamentos y fechas de vencimiento (Kardex).
* Transacciones de pago digitales reales con pasarelas externas (Mercado Pago / PayPal).
* EmisiÃ³n automÃ¡tica de Boletas y Facturas electrÃ³nicas con validez tributaria.

---

## 3. Estructura del Equipo (4 Integrantes)
El proyecto cumple la regla de asignaciÃ³n estricta de la cÃ¡tedra: **2 microservicios por integrante (uno transaccional con cabecera-detalle y uno no transaccional)**:

```mermaid
flowchart TD
    subgraph Eq["Equipo MediZano (4 Integrantes)"]
        Int1["Integrante 1\n(Ventas & CatÃ¡logo)"]
        Int2["Integrante 2\n(Pagos & Clientes)"]
        Int3 = "Integrante 3\n(FacturaciÃ³n & Seguridad)"
        Int4["Integrante 4\n(Inventario & Proveedores)"]
    end

    subgraph MS["8 Microservicios de Negocio"]
        MS_Ord["orden-ms (Transaccional)"]
        MS_Cat["catalogo-ms (No Transaccional)"]
        MS_Pag["pago-ms (Transaccional)"]
        MS_Cli["cliente-ms (No Transaccional)"]
        MS_Fac["facturacion-ms (Transaccional)"]
        MS_Usu["usuario-ms (No Transaccional)"]
        MS_Inv["inventario-ms (Transaccional)"]
        MS_Pro["proveedor-ms (No Transaccional)"]
    end

    Int1 --> MS_Ord & MS_Cat
    Int2 --> MS_Pag & MS_Cli
    Int3 --> MS_Fac & MS_Usu
    Int4 --> MS_Inv & MS_Pro
```

---

## 4. Cronograma de Hitos AcadÃ©micos

| Hito | sesión | Producto Entregable |
| :---: | :---: | :--- |
| **Hito 1** | **S2** | **Brief TÃ©cnico:** 4 integrantes, 8 microservicios asignados, entidades y endpoints. |
| **Hito 2** | **S5** | **Producto U1:** Sistema distribuido base con Eureka, Config Server y API Gateway. |
| **Hito 3** | **S12** | **Producto U2:** Sistema robusto con Resiliencia (Feign), Seguridad JWT, Kafka, Saga y Observabilidad. |
| **Hito 4** | **S15 / S16** | **Producto Final:** ValidaciÃ³n end-to-end, estabilizaciÃ³n en Docker y defensa tÃ©cnica. |

