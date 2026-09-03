package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.FacturaDTO;
import com.medizano.facturacion.dto.GenerarFacturaRequest;
import com.medizano.facturacion.entity.Factura;
import com.medizano.facturacion.service.FacturacionService;
import com.medizano.facturacion.service.PdfFacturaService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/facturacion")
@RequiredArgsConstructor
@Tag(name = "Facturación", description = "Microservicio Transaccional de Facturación Electrónica y Comprobantes PDF (Rubén)")
public class FacturacionController {

    private final FacturacionService facturacionService;
    private final PdfFacturaService pdfFacturaService;

    @GetMapping
    @Operation(summary = "Listar facturas recientes")
    public ResponseEntity<List<FacturaDTO>> listar() {
        return ResponseEntity.ok(facturacionService.listarFacturas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener factura por ID")
    public ResponseEntity<FacturaDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(facturacionService.obtenerPorId(id));
    }

    @GetMapping("/orden/{ordenId}")
    @Operation(summary = "Obtener factura por ID de orden")
    public ResponseEntity<FacturaDTO> obtenerPorOrdenId(@PathVariable Long ordenId) {
        return ResponseEntity.ok(facturacionService.obtenerPorOrdenId(ordenId));
    }

    @PostMapping("/generar")
    @Operation(summary = "Generar comprobante/factura electrónica")
    public ResponseEntity<FacturaDTO> generarFactura(@Valid @RequestBody GenerarFacturaRequest request) {
        FacturaDTO dto = facturacionService.generarFactura(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar comprobante en PDF por ID de factura")
    public ResponseEntity<byte[]> descargarPdfPorId(@PathVariable Long id) {
        Factura factura = facturacionService.obtenerEntidadPorId(id);
        byte[] pdfBytes = pdfFacturaService.generarComprobantePdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Comprobante_" + factura.getNumeroFactura() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping("/orden/{ordenId}/pdf")
    @Operation(summary = "Descargar comprobante en PDF por ID de orden")
    public ResponseEntity<byte[]> descargarPdfPorOrdenId(@PathVariable Long ordenId) {
        Factura factura = facturacionService.obtenerEntidadPorOrdenId(ordenId);
        byte[] pdfBytes = pdfFacturaService.generarComprobantePdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Comprobante_" + factura.getNumeroFactura() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}

