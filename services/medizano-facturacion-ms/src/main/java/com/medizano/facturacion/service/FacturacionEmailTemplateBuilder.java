package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptItemDTO;
import com.medizano.facturacion.dto.EmailReceiptRequest;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FacturacionEmailTemplateBuilder {

    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter PERU_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.forLanguageTag("es-PE"));

    public static String buildHtmlReceipt(EmailReceiptRequest req) {
        StringBuilder itemsHtml = new StringBuilder();
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            for (EmailReceiptItemDTO it : req.getItems()) {
                BigDecimal unitPrice = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal itemTotal = it.getTotal() != null ? it.getTotal() : BigDecimal.ZERO;
                itemsHtml.append("<tr>")
                        .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px; color: #1e293b;\">")
                        .append(escapeHtml(it.getName() != null ? it.getName() : "Medicamento"))
                        .append("</td>")
                        .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px; color: #475569; text-align: center;\">")
                        .append(it.getQuantity() != null ? it.getQuantity() : 1)
                        .append("</td>")
                        .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px; color: #475569; text-align: right;\">S/ ")
                        .append(String.format("%.2f", unitPrice))
                        .append("</td>")
                        .append("<td style=\"padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px; color: #0f172a; text-align: right; font-weight: 600;\">S/ ")
                        .append(String.format("%.2f", itemTotal))
                        .append("</td>")
                        .append("</tr>");
            }
        } else {
            itemsHtml.append("<tr><td colspan=\"4\" style=\"padding: 12px; text-align: center; color: #94a3b8; font-size: 13px;\">Venta de mostrador</td></tr>");
        }

        String fechaEmision = req.getDate() != null && !req.getDate().isEmpty() 
                ? req.getDate() 
                : ZonedDateTime.now(PERU_ZONE).format(PERU_FORMATTER);

        String series = req.getSeriesNumber() != null ? req.getSeriesNumber() : "B001-00000001";
        String customerName = req.getCustomerName() != null ? req.getCustomerName() : "Cliente General";
        String customerDoc = req.getCustomerDoc() != null ? req.getCustomerDoc() : "Sin documento";
        String paymentMode = req.getPaymentMode() != null ? req.getPaymentMode() : "EFECTIVO";

        BigDecimal subtotal = req.getSubtotal() != null ? req.getSubtotal() : BigDecimal.ZERO;
        BigDecimal igv = req.getIgv() != null ? req.getIgv() : BigDecimal.ZERO;
        BigDecimal total = req.getTotal() != null ? req.getTotal() : BigDecimal.ZERO;

        return "<!DOCTYPE html>" +
                "<html lang=\"es\">" +
                "<head>" +
                "  <meta charset=\"UTF-8\" />" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
                "  <title>Comprobante de Venta - MediZano Botica</title>" +
                "</head>" +
                "<body style=\"margin: 0; padding: 20px 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;\">" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">" +
                "    <tr>" +
                "      <td align=\"center\">" +
                "        <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"560\" style=\"max-width: 560px; width: 100%; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0;\">" +
                
                "          <!-- ENCABEZADO LIMPIO -->" +
                "          <tr>" +
                "            <td style=\"background-color: #1e3a8a; padding: 24px 28px; text-align: left;\">" +
                "              <h2 style=\"margin: 0; color: #ffffff; font-size: 22px; font-weight: 700;\">MediZano Botica</h2>" +
                "              <p style=\"margin: 4px 0 0 0; color: #93c5fd; font-size: 13px;\">Comprobante de Venta Digital</p>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- DATOS PRINCIPALES -->" +
                "          <tr>" +
                "            <td style=\"padding: 20px 28px 12px 28px;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                <tr>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b; width: 35%;\">Comprobante:</td>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13.5px; color: #1e3a8a; font-weight: 700;\">" + escapeHtml(series) + "</td>" +
                "                </tr>" +
                "                <tr>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b;\">Fecha (Perú):</td>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #334155; font-weight: 600;\">" + escapeHtml(fechaEmision) + "</td>" +
                "                </tr>" +
                "                <tr>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b;\">Cliente:</td>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13.5px; color: #0f172a; font-weight: 600;\">" + escapeHtml(customerName) + "</td>" +
                "                </tr>" +
                "                <tr>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b;\">Documento:</td>" +
                "                  <td style=\"padding-bottom: 6px; font-size: 13px; color: #334155;\">" + escapeHtml(customerDoc) + "</td>" +
                "                </tr>" +
                "                <tr>" +
                "                  <td style=\"font-size: 13px; color: #64748b;\">Método de Pago:</td>" +
                "                  <td style=\"font-size: 13px; color: #0f172a; font-weight: 600;\">" + escapeHtml(paymentMode) + "</td>" +
                "                </tr>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- TABLA DE DETALLE -->" +
                "          <tr>" +
                "            <td style=\"padding: 12px 28px;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse: collapse; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;\">" +
                "                <thead>" +
                "                  <tr style=\"background: #f1f5f9;\">" +
                "                    <th style=\"padding: 10px 12px; text-align: left; font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase;\">Producto</th>" +
                "                    <th style=\"padding: 10px 12px; text-align: center; font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase;\">Cant.</th>" +
                "                    <th style=\"padding: 10px 12px; text-align: right; font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase;\">P. Unit</th>" +
                "                    <th style=\"padding: 10px 12px; text-align: right; font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase;\">Total</th>" +
                "                  </tr>" +
                "                </thead>" +
                "                <tbody>" +
                itemsHtml.toString() +
                "                </tbody>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- TOTALES -->" +
                "          <tr>" +
                "            <td style=\"padding: 12px 28px 24px 28px;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                <tr>" +
                "                  <td width=\"50%\"></td>" +
                "                  <td width=\"50%\">" +
                "                    <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                      <tr>" +
                "                        <td style=\"padding: 4px 0; font-size: 13px; color: #64748b;\">Subtotal:</td>" +
                "                        <td style=\"padding: 4px 0; font-size: 13px; color: #1e293b; text-align: right;\">S/ " + String.format("%.2f", subtotal) + "</td>" +
                "                      </tr>" +
                "                      <tr>" +
                "                        <td style=\"padding: 4px 0; font-size: 13px; color: #64748b;\">IGV (18%):</td>" +
                "                        <td style=\"padding: 4px 0; font-size: 13px; color: #1e293b; text-align: right;\">S/ " + String.format("%.2f", igv) + "</td>" +
                "                      </tr>" +
                "                      <tr style=\"border-top: 2px solid #e2e8f0;\">" +
                "                        <td style=\"padding: 8px 0; font-size: 15px; font-weight: 700; color: #0f172a;\">Total:</td>" +
                "                        <td style=\"padding: 8px 0; font-size: 18px; font-weight: 800; color: #047857; text-align: right;\">S/ " + String.format("%.2f", total) + "</td>" +
                "                      </tr>" +
                "                    </table>" +
                "                  </td>" +
                "                </tr>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- PIE LIMPIO -->" +
                "          <tr>" +
                "            <td style=\"background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 28px; text-align: center;\">" +
                "              <p style=\"margin: 0; font-size: 12.5px; color: #475569;\">Gracias por su compra en <strong>MediZano Botica</strong>.</p>" +
                "            </td>" +
                "          </tr>" +

                "        </table>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</body>" +
                "</html>";
    }

    public static String buildPlainTextReceipt(EmailReceiptRequest req) {
        String fecha = req.getDate() != null && !req.getDate().isEmpty() 
                ? req.getDate() 
                : ZonedDateTime.now(PERU_ZONE).format(PERU_FORMATTER);

        StringBuilder sb = new StringBuilder();
        sb.append("MEDIZANO BOTICA - COMPROBANTE DE VENTA\n");
        sb.append("=========================================\n");
        sb.append("Comprobante: ").append(req.getSeriesNumber() != null ? req.getSeriesNumber() : "B001-00000001").append("\n");
        sb.append("Fecha (Perú): ").append(fecha).append("\n");
        sb.append("Cliente: ").append(req.getCustomerName() != null ? req.getCustomerName() : "Cliente General").append("\n");
        sb.append("Documento: ").append(req.getCustomerDoc() != null ? req.getCustomerDoc() : "Sin documento").append("\n");
        sb.append("Pago: ").append(req.getPaymentMode() != null ? req.getPaymentMode() : "EFECTIVO").append("\n");
        sb.append("-----------------------------------------\n");
        sb.append("PRODUCTOS:\n");
        if (req.getItems() != null) {
            for (EmailReceiptItemDTO it : req.getItems()) {
                sb.append(String.format("  - %dx %s: S/ %.2f\n", 
                        it.getQuantity() != null ? it.getQuantity() : 1,
                        it.getName() != null ? it.getName() : "Item",
                        it.getTotal() != null ? it.getTotal() : BigDecimal.ZERO));
            }
        }
        sb.append("-----------------------------------------\n");
        sb.append(String.format("Subtotal: S/ %.2f\n", req.getSubtotal() != null ? req.getSubtotal() : BigDecimal.ZERO));
        sb.append(String.format("IGV (18%%): S/ %.2f\n", req.getIgv() != null ? req.getIgv() : BigDecimal.ZERO));
        sb.append(String.format("TOTAL: S/ %.2f\n", req.getTotal() != null ? req.getTotal() : BigDecimal.ZERO));
        sb.append("=========================================\n");
        sb.append("Gracias por su compra en MediZano Botica.\n");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
