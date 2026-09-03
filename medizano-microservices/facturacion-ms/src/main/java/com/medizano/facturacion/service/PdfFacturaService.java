package com.medizano.facturacion.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.medizano.facturacion.entity.Factura;
import com.medizano.facturacion.entity.ItemFactura;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfFacturaService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generarComprobantePdf(Factura factura) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4);
            Document document = new Document(pdfDoc);
            document.setMargins(25, 25, 25, 25);

            DeviceRgb primaryColor = new DeviceRgb(37, 99, 235);

            // Header
            Paragraph title = new Paragraph("BOTICA MEDIZANO")
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(primaryColor)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Sistema de Gestión Farmacéutica y Punto de Venta\nRUC: 20608945612 | Tel: (01) 456-7890")
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(subtitle);

            document.add(new Paragraph("==========================================================================")
                    .setFontSize(8).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));

            // Info Table
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            infoTable.addCell(createCleanCell("Comprobante: " + factura.getNumeroFactura(), true));
            infoTable.addCell(createCleanCell("Fecha: " + factura.getCreatedAt().format(DATE_FORMATTER), false));
            infoTable.addCell(createCleanCell("Orden Ref: " + factura.getNumeroOrden(), false));
            infoTable.addCell(createCleanCell("Cliente: " + (factura.getClienteNombre() != null ? factura.getClienteNombre() : "Cliente General"), false));
            infoTable.addCell(createCleanCell("Método de Pago: " + (factura.getMetodoPago() != null ? factura.getMetodoPago() : "EFECTIVO"), false));
            infoTable.addCell(createCleanCell("Ref. Transacción: " + (factura.getReferenciaPago() != null ? factura.getReferenciaPago() : "N/A"), false));
            document.add(infoTable);

            document.add(new Paragraph("\n"));

            // Items Table
            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{10, 45, 15, 15, 15})).useAllAvailableWidth();
            itemsTable.addHeaderCell(createHeaderCell("Item", primaryColor));
            itemsTable.addHeaderCell(createHeaderCell("Descripción", primaryColor));
            itemsTable.addHeaderCell(createHeaderCell("Cant.", primaryColor));
            itemsTable.addHeaderCell(createHeaderCell("P. Unit", primaryColor));
            itemsTable.addHeaderCell(createHeaderCell("Subtotal", primaryColor));

            int count = 1;
            for (ItemFactura item : factura.getItems()) {
                itemsTable.addCell(new Cell().add(new Paragraph(String.valueOf(count++))).setFontSize(9));
                itemsTable.addCell(new Cell().add(new Paragraph(item.getProductoNombre() != null ? item.getProductoNombre() : "Producto " + item.getProductoId())).setFontSize(9));
                itemsTable.addCell(new Cell().add(new Paragraph(String.valueOf(item.getCantidad()))).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
                itemsTable.addCell(new Cell().add(new Paragraph("S/ " + formatMoney(item.getPrecioUnitario()))).setFontSize(9).setTextAlignment(TextAlignment.RIGHT));
                itemsTable.addCell(new Cell().add(new Paragraph("S/ " + formatMoney(item.getSubtotal()))).setFontSize(9).setTextAlignment(TextAlignment.RIGHT));
            }
            document.add(itemsTable);

            document.add(new Paragraph("\n"));

            // Totals Table
            Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{70, 30})).useAllAvailableWidth();
            totalsTable.addCell(createCleanCell("Subtotal: ", true).setTextAlignment(TextAlignment.RIGHT));
            totalsTable.addCell(createCleanCell("S/ " + formatMoney(factura.getSubtotal()), false).setTextAlignment(TextAlignment.RIGHT));

            totalsTable.addCell(createCleanCell("IGV (18%): ", true).setTextAlignment(TextAlignment.RIGHT));
            totalsTable.addCell(createCleanCell("S/ " + formatMoney(factura.getImpuesto()), false).setTextAlignment(TextAlignment.RIGHT));

            totalsTable.addCell(createCleanCell("TOTAL PAGADO: ", true).setFontSize(11).setFontColor(primaryColor).setTextAlignment(TextAlignment.RIGHT));
            totalsTable.addCell(createCleanCell("S/ " + formatMoney(factura.getTotal()), true).setFontSize(11).setFontColor(primaryColor).setTextAlignment(TextAlignment.RIGHT));
            document.add(totalsTable);

            // Footer
            Paragraph footer = new Paragraph("\n¡Gracias por su compra en MediZano!\nComprobante electrónico emitido con éxito.")
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.DARK_GRAY);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("Error al generar comprobante PDF para factura {}: {}", factura.getNumeroFactura(), ex.getMessage(), ex);
            throw new RuntimeException("Error al generar PDF de la factura");
        }
    }

    private Cell createHeaderCell(String text, DeviceRgb bgColor) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(bgColor)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell createCleanCell(String text, boolean bold) {
        Paragraph p = new Paragraph(text).setFontSize(9);
        if (bold) p.setBold();
        return new Cell().add(p).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
    }

    private String formatMoney(BigDecimal amount) {
        return String.format("%.2f", amount != null ? amount : BigDecimal.ZERO);
    }
}

