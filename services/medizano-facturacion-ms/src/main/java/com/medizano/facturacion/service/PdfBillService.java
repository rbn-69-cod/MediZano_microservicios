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
import com.medizano.facturacion.dto.BillItemResponse;
import com.medizano.facturacion.dto.BillResponse;
import com.medizano.facturacion.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class PdfBillService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public byte[] generateBillPdf(BillResponse bill) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);

        // Ancho estándar de papel térmico ticketera 80mm: 80 mm * 72 / 25.4 = 226.77 pt
        float ticketWidth = 226.77f;
        int itemCount = (bill.getItems() != null && !bill.getItems().isEmpty()) ? bill.getItems().size() : 1;
        float estimatedHeight = Math.max(420f, 290f + (itemCount * 32f));
        PageSize ticketPage = new PageSize(ticketWidth, estimatedHeight);

        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(ticketPage);
        Document document = new Document(pdf, ticketPage);
        document.setMargins(10, 8, 10, 8);

        try {
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.COURIER_BOLD);
            PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.COURIER);

            // 1. Encabezado Botica
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

            String billNum = bill.getBillNumber() != null ? bill.getBillNumber() : "FAC-" + bill.getId();
            Paragraph numPara = new Paragraph(billNum)
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

            String fechaStr = bill.getBillDate() != null ? bill.getBillDate().format(DATE_TIME_FORMATTER) : "N/A";
            addTicketMetaRow(metaTable, "FECHA:", fechaStr, boldFont, normalFont);

            String cajero = bill.getCashierName() != null && !bill.getCashierName().isBlank() ? bill.getCashierName().trim() : "Administrador";
            addTicketMetaRow(metaTable, "CAJERO:", cajero, boldFont, normalFont);

            String cliente = bill.getCustomerName() != null && !bill.getCustomerName().isBlank() && !"null".equalsIgnoreCase(bill.getCustomerName().trim())
                    ? bill.getCustomerName().trim() : "Cliente general";
            addTicketMetaRow(metaTable, "CLIENTE:", cliente, boldFont, normalFont);

            if (bill.getCustomerPhone() != null && !bill.getCustomerPhone().isBlank() && !"null".equalsIgnoreCase(bill.getCustomerPhone())) {
                addTicketMetaRow(metaTable, "TELÉFONO:", bill.getCustomerPhone().trim(), boldFont, normalFont);
            }

            if (bill.getCustomerEmail() != null && !bill.getCustomerEmail().isBlank() && !"null".equalsIgnoreCase(bill.getCustomerEmail())) {
                addTicketMetaRow(metaTable, "EMAIL:", bill.getCustomerEmail().trim(), boldFont, normalFont);
            }
            document.add(metaTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 4. Tabla de Medicamentos / Ítems
            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{12, 48, 20, 20})).useAllAvailableWidth();
            itemsTable.setMarginBottom(3);

            itemsTable.addHeaderCell(createTicketHeaderCell("CANT", boldFont, TextAlignment.CENTER));
            itemsTable.addHeaderCell(createTicketHeaderCell("DESCRIPCION", boldFont, TextAlignment.LEFT));
            itemsTable.addHeaderCell(createTicketHeaderCell("P.UNIT", boldFont, TextAlignment.RIGHT));
            itemsTable.addHeaderCell(createTicketHeaderCell("TOTAL", boldFont, TextAlignment.RIGHT));

            if (bill.getItems() != null) {
                for (BillItemResponse item : bill.getItems()) {
                    itemsTable.addCell(createCleanCell(String.valueOf(item.getQuantity()), boldFont, 7.5f, TextAlignment.CENTER));

                    String desc = item.getMedicineName() != null ? item.getMedicineName() : "Producto";
                    if (item.getBatchNumber() != null && !item.getBatchNumber().isBlank()) {
                        desc += "\n Lt: " + item.getBatchNumber();
                    }
                    itemsTable.addCell(createCleanCell(desc, normalFont, 7f, TextAlignment.LEFT));

                    itemsTable.addCell(createCleanCell(formatMoney(item.getUnitPrice()), normalFont, 7f, TextAlignment.RIGHT));

                    BigDecimal totalItem = item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO;
                    itemsTable.addCell(createCleanCell(formatMoney(totalItem), boldFont, 7f, TextAlignment.RIGHT));
                }
            }
            document.add(itemsTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 5. Totales
            Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            totalsTable.setMarginBottom(3);

            BigDecimal subtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
            BigDecimal igv = bill.getTotalGst() != null ? bill.getTotalGst() : BigDecimal.ZERO;
            BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;

            addTicketTotalRow(totalsTable, "OP. GRAVADA (Subtotal):", "S/ " + formatMoney(subtotal), normalFont, normalFont, 7.5f);
            addTicketTotalRow(totalsTable, "I.G.V. (18%):", "S/ " + formatMoney(igv), normalFont, normalFont, 7.5f);
            addTicketTotalRow(totalsTable, "IMPORTE TOTAL:", "S/ " + formatMoney(total), boldFont, boldFont, 9f);
            document.add(totalsTable);

            document.add(new Paragraph("------------------------------------------------")
                    .setFont(normalFont).setFontSize(7).setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));

            // 6. Forma de Pago
            Table payTable = new Table(UnitValue.createPercentArray(new float[]{45, 55})).useAllAvailableWidth();
            payTable.setMarginBottom(4);

            String metodo = "EFECTIVO";
            if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                PaymentResponse p = bill.getPayments().get(0);
                if (p.getMode() != null) {
                    metodo = p.getMode().toString();
                }
            }
            addTicketMetaRow(payTable, "FORMA DE PAGO:", metodo, boldFont, normalFont);

            if ("CASH".equalsIgnoreCase(metodo) || "EFECTIVO".equalsIgnoreCase(metodo)) {
                BigDecimal recibido = bill.getCashTendered() != null && bill.getCashTendered().compareTo(BigDecimal.ZERO) > 0
                        ? bill.getCashTendered() : total;
                BigDecimal vuelto = bill.getChangeAmount() != null && bill.getChangeAmount().compareTo(BigDecimal.ZERO) >= 0
                        ? bill.getChangeAmount() : BigDecimal.ZERO;
                addTicketMetaRow(payTable, "PAGO CON:", "S/ " + formatMoney(recibido), normalFont, normalFont);
                addTicketMetaRow(payTable, "VUELTO:", "S/ " + formatMoney(vuelto), boldFont, boldFont);
            }
            document.add(payTable);

            // 7. Footer térmico SUNAT
            Paragraph footer = new Paragraph("Representación impresa de la Boleta de Venta Electrónica.\nAutorizado mediante resolución SUNAT.\n¡Gracias por su preferencia en MediZano!\nConserve este ticket para reclamos.")
                    .setFont(normalFont)
                    .setFontSize(6.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.BLACK)
                    .setMarginTop(2);
            document.add(footer);

        } finally {
            document.close();
        }

        return baos.toByteArray();
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
