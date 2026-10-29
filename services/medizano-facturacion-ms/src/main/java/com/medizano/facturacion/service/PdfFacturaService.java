package com.medizano.facturacion.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public byte[] generarComprobantePdf(Factura factura) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);

            // Ancho estándar de papel térmico ticketera 80mm: 80 mm * 72 / 25.4 = 226.77 pt
            float ticketWidth = 226.77f;
            int itemCount = (factura.getItems() != null && !factura.getItems().isEmpty()) ? factura.getItems().size() : 1;
            float estimatedHeight = Math.max(420f, 290f + (itemCount * 32f));
            PageSize ticketPage = new PageSize(ticketWidth, estimatedHeight);

            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(ticketPage);
            Document document = new Document(pdfDoc, ticketPage);
            document.setMargins(10, 8, 10, 8);

            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.COURIER_BOLD);
            PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.COURIER);

            // 1. Header
            Paragraph brandHeader = new Paragraph("MEDIZANO")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(1);
            document.add(brandHeader);

            Paragraph brandSub = new Paragraph("BOTICA & FARMACIA ESPECIALIZADA")
                    .setFont(boldFont)
                    .setFontSize(7.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(1);
            document.add(brandSub);

            Paragraph brandInfo = new Paragraph("RUC: 20608945123 · Av. Larco 742, Miraflores\nTel: (01) 456-7890 · Lima - Perú")
                    .setFont(normalFont)
                    .setFontSize(6.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(3);
            document.add(brandInfo);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 2. Tipo y Número de Comprobante
            Paragraph docType = new Paragraph("BOLETA DE VENTA ELECTRÓNICA")
                    .setFont(boldFont)
                    .setFontSize(8.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(2);
            document.add(docType);

            Paragraph numPara = new Paragraph(factura.getNumeroFactura())
                    .setFont(boldFont)
                    .setFontSize(9.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(3);
            document.add(numPara);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 3. Metadatos
            Table metaTable = new Table(UnitValue.createPercentArray(new float[]{28, 72})).useAllAvailableWidth();
            metaTable.setMarginBottom(3);

            String fechaStr = factura.getCreatedAt() != null ? factura.getCreatedAt().format(DATE_FORMATTER) : "N/A";
            addTicketMetaRow(metaTable, "FECHA:", fechaStr, boldFont, normalFont);

            addTicketMetaRow(metaTable, "CAJERO:", "Administrador", boldFont, normalFont);

            String cliente = factura.getClienteNombre() != null && !factura.getClienteNombre().isBlank()
                    ? factura.getClienteNombre().trim() : "Cliente general";
            addTicketMetaRow(metaTable, "CLIENTE:", cliente, boldFont, normalFont);

            if (factura.getNumeroOrden() != null && !factura.getNumeroOrden().isBlank()) {
                addTicketMetaRow(metaTable, "ORDEN:", factura.getNumeroOrden().trim(), boldFont, normalFont);
            }
            document.add(metaTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 4. Items Table
            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{12, 48, 20, 20})).useAllAvailableWidth();
            itemsTable.setMarginBottom(3);

            itemsTable.addHeaderCell(createTicketHeaderCell("CANT", boldFont, TextAlignment.CENTER));
            itemsTable.addHeaderCell(createTicketHeaderCell("DESCRIPCION", boldFont, TextAlignment.LEFT));
            itemsTable.addHeaderCell(createTicketHeaderCell("P.UNIT", boldFont, TextAlignment.RIGHT));
            itemsTable.addHeaderCell(createTicketHeaderCell("TOTAL", boldFont, TextAlignment.RIGHT));

            if (factura.getItems() != null) {
                for (ItemFactura item : factura.getItems()) {
                    itemsTable.addCell(createCleanCell(String.valueOf(item.getCantidad()), boldFont, 7.5f, TextAlignment.CENTER));

                    String desc = item.getProductoNombre() != null ? item.getProductoNombre() : "Producto " + item.getProductoId();
                    itemsTable.addCell(createCleanCell(desc, normalFont, 7f, TextAlignment.LEFT));

                    itemsTable.addCell(createCleanCell(formatMoney(item.getPrecioUnitario()), normalFont, 7f, TextAlignment.RIGHT));

                    itemsTable.addCell(createCleanCell(formatMoney(item.getSubtotal()), boldFont, 7f, TextAlignment.RIGHT));
                }
            }
            document.add(itemsTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 5. Totals Table
            Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            totalsTable.setMarginBottom(3);

            addTicketTotalRow(totalsTable, "OP. GRAVADA (Subtotal):", "S/ " + formatMoney(factura.getSubtotal()), normalFont, normalFont, 7.5f);
            addTicketTotalRow(totalsTable, "I.G.V. (18%):", "S/ " + formatMoney(factura.getImpuesto()), normalFont, normalFont, 7.5f);
            addTicketTotalRow(totalsTable, "IMPORTE TOTAL:", "S/ " + formatMoney(factura.getTotal()), boldFont, boldFont, 9f);
            document.add(totalsTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 6. Pago
            Table payTable = new Table(UnitValue.createPercentArray(new float[]{45, 55})).useAllAvailableWidth();
            payTable.setMarginBottom(4);
            addTicketMetaRow(payTable, "FORMA DE PAGO:", factura.getMetodoPago() != null ? factura.getMetodoPago() : "EFECTIVO", boldFont, normalFont);
            if (factura.getReferenciaPago() != null && !factura.getReferenciaPago().isBlank()) {
                addTicketMetaRow(payTable, "REFERENCIA:", factura.getReferenciaPago().trim(), normalFont, normalFont);
            }
            document.add(payTable);

            // 7. Footer
            Paragraph footer = new Paragraph("Representación impresa de la Boleta de Venta Electrónica.\nAutorizado mediante resolución SUNAT.\n¡Gracias por su preferencia en MediZano!\nConserve este ticket para reclamos.")
                    .setFont(normalFont)
                    .setFontSize(6.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.BLACK)
                    .setMarginTop(2);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("Error al generar comprobante ticket PDF para factura {}: {}", factura.getNumeroFactura(), ex.getMessage(), ex);
            throw new RuntimeException("Error al generar ticket PDF de la factura");
        }
    }

    private void addTicketMetaRow(Table table, String label, String value, PdfFont labelFont, PdfFont valueFont) {
        Cell c1 = new Cell().add(new Paragraph(label).setFont(labelFont).setFontSize(7f)).setBorder(Border.NO_BORDER).setPadding(1);
        Cell c2 = new Cell().add(new Paragraph(value != null ? value : "").setFont(valueFont).setFontSize(7f)).setBorder(Border.NO_BORDER).setPadding(1);
        table.addCell(c1);
        table.addCell(c2);
    }

    private void addTicketTotalRow(Table table, String label, String value, PdfFont labelFont, PdfFont valueFont, float size) {
        Paragraph p1 = new Paragraph(label).setFont(labelFont).setFontSize(size);
        Paragraph p2 = new Paragraph(value).setFont(valueFont).setFontSize(size);
        Cell c1 = new Cell().add(p1).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPadding(1.5f);
        Cell c2 = new Cell().add(p2).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPadding(1.5f);
        table.addCell(c1);
        table.addCell(c2);
    }

    private Cell createTicketHeaderCell(String text, PdfFont font, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(7f))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(align)
                .setPadding(2);
    }

    private Cell createCleanCell(String text, PdfFont font, float size, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(size))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(align)
                .setPadding(1.5f);
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00";
    }
}
