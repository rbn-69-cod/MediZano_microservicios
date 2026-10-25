# S01 - Construcción de un Servicio Base para MediZano POS (`catalogo-ms`)

> **Sesión S01 · Desarrollo de Aplicaciones Distribuidas (UPeU)**  
> **Componente:** `services/medizano-catalogo-ms` (Puerto `:8081`)  
> **Responsable:** Aldo Calla (Microservicio No Transaccional)

---

## 1. Propósito Arquitectónico

Construir el primer microservicio REST base del sistema (**`catalogo-ms`**), estableciendo el estándar de diseño que deben seguir todos los microservicios del proyecto MediZano:

1. **Arquitectura en Capas:** Controller $\rightarrow$ Service $\rightarrow$ Repository $\rightarrow$ Database.
2. **Desacoplamiento DTO / Entity:** Mapeo estricto entre entidades de base de datos y objetos de transferencia (DTO) con MapStruct.
3. **Persistencia Independiente:** Base de datos `medizano_catalogo_db` aislada sin acoplamientos a otros servicios.
4. **Documentación Swagger / OpenAPI 3:** Exposición automática de contratos REST en `/swagger-ui.html`.
5. **Observabilidad Base:** Métricas de salud (`/actuator/health`) y métricas de rendimiento para Prometheus (`/actuator/prometheus`).

---

## 2. Tecnologías y Dependencias (`pom.xml`)

```xml
<dependencies>
    <!-- Spring Web & Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Persistencia JPA y PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Service Discovery -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <!-- Observabilidad & Métricas -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- Utilidades & Mappings -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 3. Entidad de Dominio (`Medicine.java`)

```java
package com.medizano.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 100)
    private String category;

    @Column(unique = true, length = 50)
    private String barcode;

    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicineStatus status; // ACTIVE, INACTIVE

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = MedicineStatus.ACTIVE;
    }
}
```

---

## 4. Controlador REST (`MedicineController.java`)

```java
package com.medizano.catalogo.controller;

import com.medizano.catalogo.dto.MedicineDTO;
import com.medizano.catalogo.service.MedicineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacist/medicines")
@RequiredArgsConstructor
public class MedicineController {

    private final MedicineService medicineService;

    @GetMapping
    public ResponseEntity<List<MedicineDTO>> getAllMedicines() {
        return ResponseEntity.ok(medicineService.getAllMedicines());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicineDTO> getMedicineById(@PathVariable Long id) {
        return ResponseEntity.ok(medicineService.getMedicineById(id));
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<MedicineDTO> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(medicineService.getByBarcode(barcode));
    }

    @PostMapping
    public ResponseEntity<MedicineDTO> createMedicine(@RequestBody MedicineDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicineService.createMedicine(dto));
    }
}
```

---

## 5. Pruebas y Evidencias de Funcionamiento

### Prueba 1: Endpoint de Salud de Spring Boot Actuator
```bash
curl -s http://localhost:8081/actuator/health
```
**Respuesta:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "discoveryComposite": {
      "status": "UP",
      "components": {
        "eureka": {
          "status": "UP"
        }
      }
    }
  }
}
```

### Prueba 2: Búsqueda por Código de Barras (Escáner POS)
```bash
curl -s http://localhost:8090/api/pharmacist/medicines/barcode/7751234567890
```
**Respuesta (Paracetamol 500 mg):**
```json
{
  "id": 1,
  "name": "Paracetamol 500 mg",
  "manufacturer": "Portugal",
  "category": "Analgésico / Antipirético",
  "barcode": "7751234567890",
  "sellingPrice": 5.00,
  "status": "ACTIVE"
}
```
