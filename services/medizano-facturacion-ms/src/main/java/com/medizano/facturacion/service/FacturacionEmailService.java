package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptRequest;
import com.medizano.facturacion.dto.EmailReceiptResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturacionEmailService {

    private final JavaMailSender defaultMailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:mailpit}")
    private String mailHost;

    @Value("${spring.mail.port:1025}")
    private int mailPort;

    public EmailReceiptResponse enviarComprobante(EmailReceiptRequest request) {
        String recipient = request.getTo() != null ? request.getTo().trim() : "";
        String seriesNumber = request.getSeriesNumber() != null ? request.getSeriesNumber().trim() : "B001-00000001";

        if (recipient.isEmpty()) {
            log.warn("Solicitud de envío de comprobante cancelada: No se proporcionó correo electrónico.");
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("No se proporcionó un correo electrónico válido")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }

        boolean hasAuth = (mailPassword != null && !mailPassword.isBlank());
        boolean isUniverLat = recipient.toLowerCase().endsWith("@univer.lat");
        
        String effectiveHost = hasAuth ? "mail.spacemail.com" : (isUniverLat ? "mx1.spacemail.com" : mailHost);
        int effectivePort = hasAuth ? 587 : (isUniverLat ? 25 : mailPort);

        log.info("Iniciando despacho de comprobante [{}] hacia buzón [{}] a través de {}:{} (Auth: {})...",
                seriesNumber, recipient, effectiveHost, effectivePort, hasAuth);

        try {
            JavaMailSender activeSender = getActiveSender(recipient);
            MimeMessage mimeMessage = activeSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String sender = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "facturacion@univer.lat";
            helper.setFrom(sender, "MediZano Botica - Comprobante Digital");
            helper.setTo(recipient);
            helper.setReplyTo(sender);
            helper.setSubject("Comprobante Electrónico " + seriesNumber + " - MediZano Botica");

            String htmlContent = FacturacionEmailTemplateBuilder.buildHtmlReceipt(request);
            helper.setText(htmlContent, true);

            // Encabezados RFC estándar para reputación y prevención de filtrado spam (RFC 5322 / RFC 3834)
            String cleanSeries = seriesNumber.replaceAll("[^a-zA-Z0-9-]", "");
            mimeMessage.setHeader("Message-ID", "<" + cleanSeries + "." + System.currentTimeMillis() + "@univer.lat>");
            mimeMessage.setHeader("X-Mailer", "MediZano POS Enterprise v1.0");
            mimeMessage.setHeader("X-Priority", "3");
            mimeMessage.setHeader("Auto-Submitted", "auto-generated");
            mimeMessage.setHeader("Precedence", "bulk");
            mimeMessage.setHeader("Organization", "MediZano Botica S.A.C.");

            activeSender.send(mimeMessage);

            log.info("Comprobante [{}] entregado exitosamente a {}:{} (250 OK) para [{}]",
                    seriesNumber, effectiveHost, effectivePort, recipient);

            // Registro de copia en visor Mailpit si se despachó fuera de él
            if (!"mailpit".equalsIgnoreCase(effectiveHost)) {
                try {
                    MimeMessage mirrorMsg = defaultMailSender.createMimeMessage();
                    MimeMessageHelper mirrorHelper = new MimeMessageHelper(mirrorMsg, true, "UTF-8");
                    mirrorHelper.setFrom(sender, "MediZano Botica - Comprobante Digital");
                    mirrorHelper.setTo(recipient);
                    mirrorHelper.setSubject("Comprobante Electrónico " + seriesNumber + " - MediZano Botica");
                    mirrorHelper.setText(htmlContent, true);
                    defaultMailSender.send(mirrorMsg);
                    log.info("Copia de respaldo registrada en visor Mailpit ({}:{})", mailHost, mailPort);
                } catch (Exception mirrorEx) {
                    log.warn("No se pudo registrar copia en Mailpit: {}", mirrorEx.getMessage());
                }
            }

            return EmailReceiptResponse.builder()
                    .success(true)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Comprobante enviado exitosamente a: " + recipient)
                    .timestamp(LocalDateTime.now().toString())
                    .build();

        } catch (Exception ex) {
            log.error("Fallo durante el despacho SMTP con {}:{} para [{}]: {}",
                    effectiveHost, effectivePort, recipient, ex.getMessage(), ex);
            String errorDetail = ex.getMessage();
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = ex.getClass().getSimpleName();
            }
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Error al enviar correo: " + errorDetail)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }
    }

    private JavaMailSender getActiveSender(String recipient) {
        // Opción 1: Servidor autenticado Spacemail si se provee contraseña (100% firmado DKIM, SPF Pass)
        if (mailPassword != null && !mailPassword.isBlank()) {
            JavaMailSenderImpl authenticatedSender = new JavaMailSenderImpl();
            authenticatedSender.setHost("mail.spacemail.com");
            authenticatedSender.setPort(587);
            authenticatedSender.setUsername((mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "carlos@univer.lat");
            authenticatedSender.setPassword(mailPassword.trim());
            authenticatedSender.setDefaultEncoding("UTF-8");
            Properties props = authenticatedSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");
            return authenticatedSender;
        }

        // Opción 2: Entrega directa a buzón @univer.lat vía servidor MX oficial
        if (recipient.toLowerCase().endsWith("@univer.lat")) {
            JavaMailSenderImpl spacemailSender = new JavaMailSenderImpl();
            spacemailSender.setHost("mx1.spacemail.com");
            spacemailSender.setPort(25);
            spacemailSender.setDefaultEncoding("UTF-8");
            Properties props = spacemailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");
            return spacemailSender;
        }

        // Opción 3: Mailpit local / servidor configurado por defecto
        return defaultMailSender;
    }
}
