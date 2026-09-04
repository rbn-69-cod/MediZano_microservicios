package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.BillResponse;
import com.medizano.facturacion.dto.CreateBillRequest;
import com.medizano.facturacion.service.BillingService;
import com.medizano.facturacion.service.PdfBillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashier/bills")
@RequiredArgsConstructor
@Tag(name = "Facturación POS", description = "Emisión, anulación, consulta y generación de PDFs de comprobantes de pago (Boletas y Facturas)")
public class BillingController {

    private final BillingService billingService;
    private final PdfBillService pdfBillService;

    @PostMapping
    @Operation(summary = "Crear comprobante de venta POS", description = "Emite un comprobante electrónico (Boleta o Factura) calculando automáticamente el subtotal, descuento, IGV (18%) y total.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comprobante emitido exitosamente", content = @Content(schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de comprobante inválidos (ítems vacíos o montos negativos)"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Requiere token JWT"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol CASHIER o ADMIN")
    })
    public ResponseEntity<BillResponse> createBill(@Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createBill(request));
    }

    @GetMapping
    @Operation(summary = "Listar todas las ventas", description = "Obtiene el listado cronológico de comprobantes de venta registrados por los cajeros.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de comprobantes obtenida exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<BillResponse>> getAllBills() {
        return ResponseEntity.ok(billingService.getAllBills());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener comprobante por ID", description = "Recupera la información completa de una boleta o factura con sus líneas de detalle por su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobante encontrado", content = @Content(schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado con el ID especificado")
    })
    public ResponseEntity<BillResponse> getBillById(
            @Parameter(description = "ID del comprobante", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(billingService.getBillById(id));
    }

    @GetMapping("/number/{billNumber}")
    @Operation(summary = "Obtener comprobante por número", description = "Localiza una factura o boleta mediante su número de serie y correlativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobante encontrado", content = @Content(schema = @Schema(implementation = BillResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "No existe comprobante con el número indicado")
    })
    public ResponseEntity<BillResponse> getBillByNumber(
            @Parameter(description = "Número correlativo del comprobante (ej. BILL-1709500000000)", example = "BILL-2026-0001", required = true)
            @PathVariable String billNumber) {
        return ResponseEntity.ok(billingService.getBillByBillNumber(billNumber));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar comprobante en PDF", description = "Genera dinámicamente y transmite en formato application/pdf el documento oficial del comprobante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF generado y transmitido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado")
    })
    public ResponseEntity<byte[]> downloadPdf(
            @Parameter(description = "ID del comprobante a imprimir en PDF", example = "1", required = true)
            @PathVariable Long id) {
        BillResponse bill = billingService.getBillById(id);
        byte[] pdfBytes;
        try {
            pdfBytes = pdfBillService.generateBillPdf(bill);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error generando PDF para comprobante ID: " + id, e);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "comprobante-" + bill.getBillNumber() + ".pdf");
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Anular comprobante", description = "Anula una venta emitida registrando el motivo de cancelación.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comprobante anulado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol CASHIER o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado")
    })
    public ResponseEntity<Void> cancelBill(
            @Parameter(description = "ID del comprobante a anular", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Motivo de la anulación administrativa", example = "Error en selección de medicamento por el cliente")
            @RequestParam(defaultValue = "Anulación administrativa") String reason) {
        billingService.cancelBill(id, reason);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<java.util.Map<String, Object>> handleValidationException(RuntimeException ex) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", java.time.LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
