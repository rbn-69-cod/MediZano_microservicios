package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.FacturaDTO;
import com.medizano.facturacion.dto.GenerarFacturaRequest;
import com.medizano.facturacion.entity.Factura;
import com.medizano.facturacion.service.FacturacionService;
import com.medizano.facturacion.service.PdfFacturaService;
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
@RequestMapping("/api/v1/facturacion")
@RequiredArgsConstructor
@Tag(name = "Comprobantes v1", description = "API transaccional v1 de comprobantes electrónicos y descarga de facturas asociadas a órdenes POS")
public class FacturacionController {

    private final FacturacionService facturacionService;
    private final PdfFacturaService pdfFacturaService;

    @GetMapping
    @Operation(summary = "Listar facturas recientes", description = "Devuelve el listado de comprobantes y facturas generadas por el sistema transaccional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de facturas recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<FacturaDTO>> listar() {
        return ResponseEntity.ok(facturacionService.listarFacturas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener factura por ID", description = "Obtiene los detalles del comprobante electrónico mediante su identificador primario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factura encontrada", content = @Content(schema = @Schema(implementation = FacturaDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Factura no encontrada")
    })
    public ResponseEntity<FacturaDTO> obtenerPorId(
            @Parameter(description = "ID de la factura", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(facturacionService.obtenerPorId(id));
    }

    @GetMapping("/orden/{ordenId}")
    @Operation(summary = "Obtener factura por ID de orden", description = "Localiza el comprobante de pago emitido correspondiente a una orden de venta POS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factura vinculada a la orden", content = @Content(schema = @Schema(implementation = FacturaDTO.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "No existe factura asociada al ID de orden provisto")
    })
    public ResponseEntity<FacturaDTO> obtenerPorOrdenId(
            @Parameter(description = "ID de la orden", example = "1", required = true)
            @PathVariable Long ordenId) {
        return ResponseEntity.ok(facturacionService.obtenerPorOrdenId(ordenId));
    }

    @PostMapping("/generar")
    @Operation(summary = "Generar comprobante/factura electrónica", description = "Genera un comprobante electrónico oficial para una orden pagada con desglose de impuestos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Factura generada exitosamente", content = @Content(schema = @Schema(implementation = FacturaDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de generación inválidos"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Orden de venta no encontrada")
    })
    public ResponseEntity<FacturaDTO> generarFactura(@Valid @RequestBody GenerarFacturaRequest request) {
        FacturaDTO dto = facturacionService.generarFactura(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar comprobante en PDF por ID de factura", description = "Genera y descarga en formato application/pdf el comprobante correspondiente al ID de factura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF generado y descargado"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Factura no encontrada")
    })
    public ResponseEntity<byte[]> descargarPdfPorId(
            @Parameter(description = "ID de la factura", example = "1", required = true)
            @PathVariable Long id) {
        Factura factura = facturacionService.obtenerEntidadPorId(id);
        byte[] pdfBytes = pdfFacturaService.generarComprobantePdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Comprobante_" + factura.getNumeroFactura() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping("/orden/{ordenId}/pdf")
    @Operation(summary = "Descargar comprobante en PDF por ID de orden", description = "Genera y descarga en formato application/pdf el comprobante asociado a una orden POS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF generado y descargado"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado para la orden indicada")
    })
    public ResponseEntity<byte[]> descargarPdfPorOrdenId(
            @Parameter(description = "ID de la orden", example = "1", required = true)
            @PathVariable Long ordenId) {
        Factura factura = facturacionService.obtenerEntidadPorOrdenId(ordenId);
        byte[] pdfBytes = pdfFacturaService.generarComprobantePdf(factura);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "Comprobante_" + factura.getNumeroFactura() + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}

