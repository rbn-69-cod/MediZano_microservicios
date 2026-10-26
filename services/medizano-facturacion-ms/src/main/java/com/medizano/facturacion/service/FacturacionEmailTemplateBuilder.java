package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptItemDTO;
import com.medizano.facturacion.dto.EmailReceiptRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FacturacionEmailTemplateBuilder {

    public static String buildHtmlReceipt(EmailReceiptRequest req) {
        StringBuilder itemsHtml = new StringBuilder();
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            for (EmailReceiptItemDTO it : req.getItems()) {
                BigDecimal unitPrice = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal itemTotal = it.getTotal() != null ? it.getTotal() : BigDecimal.ZERO;
                itemsHtml.append("<tr>")
                        .append("<td style=\"padding: 12px 14px; border-bottom: 1px solid #e2e8f0; font-size: 13.5px; color: #1e293b; font-weight: 500;\">")
                        .append(escapeHtml(it.getName() != null ? it.getName() : "Medicamento"))
                        .append("</td>")
                        .append("<td style=\"padding: 12px 14px; border-bottom: 1px solid #e2e8f0; font-size: 13.5px; color: #475569; text-align: center;\">")
                        .append(it.getQuantity() != null ? it.getQuantity() : 1)
                        .append("</td>")
                        .append("<td style=\"padding: 12px 14px; border-bottom: 1px solid #e2e8f0; font-size: 13.5px; color: #475569; text-align: right;\">S/ ")
                        .append(String.format("%.2f", unitPrice))
                        .append("</td>")
                        .append("<td style=\"padding: 12px 14px; border-bottom: 1px solid #e2e8f0; font-size: 13.5px; color: #0f172a; text-align: right; font-weight: 600;\">S/ ")
                        .append(String.format("%.2f", itemTotal))
                        .append("</td>")
                        .append("</tr>");
            }
        } else {
            itemsHtml.append("<tr><td colspan=\"4\" style=\"padding: 14px; text-align: center; color: #94a3b8; font-size: 13px;\">Venta general de mostrador</td></tr>");
        }

        String fechaEmision = req.getDate() != null && !req.getDate().isEmpty() 
                ? req.getDate() 
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        String series = req.getSeriesNumber() != null ? req.getSeriesNumber() : "B001-00000001";
        String customerName = req.getCustomerName() != null ? req.getCustomerName() : "Cliente General";
        String customerDoc = req.getCustomerDoc() != null ? req.getCustomerDoc() : "Sin documento";
        String cashier = req.getCashier() != null ? req.getCashier() : "Cajero en turno";
        String paymentMode = req.getPaymentMode() != null ? req.getPaymentMode() : "EFECTIVO";

        BigDecimal subtotal = req.getSubtotal() != null ? req.getSubtotal() : BigDecimal.ZERO;
        BigDecimal igv = req.getIgv() != null ? req.getIgv() : BigDecimal.ZERO;
        BigDecimal total = req.getTotal() != null ? req.getTotal() : BigDecimal.ZERO;

        return "<!DOCTYPE html>" +
                "<html lang=\"es\">" +
                "<head>" +
                "  <meta charset=\"UTF-8\" />" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
                "  <title>Boleta Electrónica MediZano</title>" +
                "</head>" +
                "<body style=\"margin: 0; padding: 24px 0; background-color: #f1f5f9; font-family: 'Segoe UI', Helvetica, Arial, sans-serif; color: #1e293b;\">" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">" +
                "    <tr>" +
                "      <td align=\"center\">" +
                "        <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"600\" style=\"max-width: 600px; width: 100%; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08); border: 1px solid #e2e8f0;\">" +
                
                "          <!-- HEADER CORPORATIVO ELEGANTE -->" +
                "          <tr>" +
                "            <td style=\"background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); padding: 32px 30px; text-align: center;\">" +
                "              <div style=\"display: inline-block; padding: 4px 12px; background: rgba(56, 189, 248, 0.2); border: 1px solid rgba(56, 189, 248, 0.4); border-radius: 20px; color: #38bdf8; font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px;\">Botica & Farmacia Especializada</div>" +
                "              <h1 style=\"margin: 0; color: #ffffff; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;\">MediZano Botica</h1>" +
                "              <p style=\"margin: 6px 0 0 0; color: #94a3b8; font-size: 13px;\">R.U.C. 20608945123 • Sede Central San Isidro • Lima, Perú</p>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- CINTA DE COMPROBANTE -->" +
                "          <tr>" +
                "            <td style=\"background: #f8fafc; padding: 16px 30px; border-bottom: 1px solid #e2e8f0;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                <tr>" +
                "                  <td>" +
                "                    <span style=\"display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px;\">Comprobante de Pago</span>" +
                "                    <strong style=\"font-size: 16px; color: #0284c7;\">" + escapeHtml(series) + "</strong>" +
                "                  </td>" +
                "                  <td align=\"right\">" +
                "                    <span style=\"display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px;\">Fecha de Emisión</span>" +
                "                    <span style=\"font-size: 13.5px; color: #334155; font-weight: 600;\">" + escapeHtml(fechaEmision) + "</span>" +
                "                  </td>" +
                "                </tr>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- BLOQUE DATOS DEL CLIENTE -->" +
                "          <tr>" +
                "            <td style=\"padding: 24px 30px;\">" +
                "              <div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 18px 20px;\">" +
                "                <h3 style=\"margin: 0 0 12px 0; font-size: 12px; font-weight: 700; color: #475569; text-transform: uppercase; letter-spacing: 0.5px;\">Datos del Cliente</h3>" +
                "                <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                  <tr>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b; width: 35%;\">Nombre / Razón Social:</td>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13.5px; color: #0f172a; font-weight: 700;\">" + escapeHtml(customerName) + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b;\">Documento Oficial:</td>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13.5px; color: #0f172a; font-weight: 600;\">" + escapeHtml(customerDoc) + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13px; color: #64748b;\">Medio de Pago:</td>" +
                "                    <td style=\"padding-bottom: 6px; font-size: 13.5px; color: #0f172a; font-weight: 600;\"><span style=\"display: inline-block; padding: 2px 8px; background: #e0f2fe; color: #0369a1; border-radius: 6px; font-size: 12px; font-weight: 700;\">" + escapeHtml(paymentMode) + "</span></td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style=\"font-size: 13px; color: #64748b;\">Cajero / Operador:</td>" +
                "                    <td style=\"font-size: 13px; color: #334155;\">" + escapeHtml(cashier) + "</td>" +
                "                  </tr>" +
                "                </table>" +
                "              </div>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- TABLA DE DETALLE DE ÍTEMS -->" +
                "          <tr>" +
                "            <td style=\"padding: 0 30px 24px 30px;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse: collapse; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;\">" +
                "                <thead>" +
                "                  <tr style=\"background: #f8fafc;\">" +
                "                    <th style=\"padding: 12px 14px; text-align: left; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; border-bottom: 2px solid #e2e8f0;\">Descripción</th>" +
                "                    <th style=\"padding: 12px 14px; text-align: center; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; border-bottom: 2px solid #e2e8f0;\">Cant.</th>" +
                "                    <th style=\"padding: 12px 14px; text-align: right; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; border-bottom: 2px solid #e2e8f0;\">P. Unit.</th>" +
                "                    <th style=\"padding: 12px 14px; text-align: right; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; border-bottom: 2px solid #e2e8f0;\">Total</th>" +
                "                  </tr>" +
                "                </thead>" +
                "                <tbody>" +
                itemsHtml.toString() +
                "                </tbody>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- TOTALES Y DESGLOSE -->" +
                "          <tr>" +
                "            <td style=\"padding: 0 30px 28px 30px;\">" +
                "              <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                <tr>" +
                "                  <td width=\"50%\"></td>" +
                "                  <td width=\"50%\">" +
                "                    <table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
                "                      <tr>" +
                "                        <td style=\"padding: 6px 0; font-size: 13.5px; color: #64748b;\">Subtotal:</td>" +
                "                        <td style=\"padding: 6px 0; font-size: 13.5px; color: #1e293b; text-align: right; font-weight: 600;\">S/ " + String.format("%.2f", subtotal) + "</td>" +
                "                      </tr>" +
                "                      <tr>" +
                "                        <td style=\"padding: 6px 0; font-size: 13.5px; color: #64748b;\">IGV (18%):</td>" +
                "                        <td style=\"padding: 6px 0; font-size: 13.5px; color: #1e293b; text-align: right; font-weight: 600;\">S/ " + String.format("%.2f", igv) + "</td>" +
                "                      </tr>" +
                "                      <tr>" +
                "                        <td colspan=\"2\" style=\"padding-top: 10px;\">" +
                "                          <div style=\"background: #ecfdf5; border: 1.5px solid #a7f3d0; border-radius: 10px; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center;\">" +
                "                            <span style=\"font-size: 14px; font-weight: 700; color: #065f46;\">TOTAL PAGADO:</span>" +
                "                            <span style=\"font-size: 20px; font-weight: 800; color: #047857; text-align: right;\">S/ " + String.format("%.2f", total) + "</span>" +
                "                          </div>" +
                "                        </td>" +
                "                      </tr>" +
                "                    </table>" +
                "                  </td>" +
                "                </tr>" +
                "              </table>" +
                "            </td>" +
                "          </tr>" +

                "          <!-- PIE DE PÁGINA FORMAL -->" +
                "          <tr>" +
                "            <td style=\"background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 24px 30px; text-align: center;\">" +
                "              <p style=\"margin: 0 0 8px 0; font-size: 12px; color: #64748b;\">Autorizado por SUNAT. Representación impresa de la Boleta de Venta Electrónica.</p>" +
                "              <p style=\"margin: 0 0 16px 0; font-size: 12px; color: #64748b;\">Consulte la validez y autenticidad de su comprobante en <a href=\"https://medizano.com/comprobantes\" style=\"color: #0284c7; text-decoration: none; font-weight: 600;\">www.medizano.com/comprobantes</a></p>" +
                "              <div style=\"padding-top: 12px; border-top: 1px dashed #cbd5e1; font-size: 12px; color: #475569;\">" +
                "                <strong>Soporte al Cliente & Botica MediZano</strong><br/>" +
                "                Correo de contacto: <a href=\"mailto:contacto@univer.lat\" style=\"color: #0284c7; text-decoration: none;\">contacto@univer.lat</a> | Central: (01) 480-1234" +
                "              </div>" +
                "              <p style=\"margin: 14px 0 0 0; font-size: 13px; font-weight: 700; color: #047857;\">¡Muchas gracias por su preferencia y por cuidar su salud con nosotros!</p>" +
                "            </td>" +
                "          </tr>" +

                "        </table>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</body>" +
                "</html>";
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

