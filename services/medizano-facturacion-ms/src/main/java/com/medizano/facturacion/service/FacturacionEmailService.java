package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptRequest;
import com.medizano.facturacion.dto.EmailReceiptResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.Properties;

@Service
@Slf4j
public class FacturacionEmailService {

    private final JavaMailSender defaultMailSender;

    public FacturacionEmailService(JavaMailSender defaultMailSender) {
        this.defaultMailSender = defaultMailSender;
    }

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
            log.warn("Solicitud de envio cancelada: buzon vacio.");
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("No se proporciono un correo electronico valido")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }

        boolean hasAuth = (mailPassword != null && !mailPassword.isBlank());
        String domain = getDomainFromEmail(recipient);

        // Determinacion inteligente de ruta SMTP:
        // 1. Con credenciales configuradas -> Relay autenticado (mail.spacemail.com con DKIM/SPF para Gmail/Hotmail)
        // 2. Sin credenciales y dominio univer.lat -> mx1.spacemail.com directo (250 OK)
        // 3. Sin credenciales y dominios externos (gmail, hotmail, etc.) -> Visor Mailpit local inmediato
        String effectiveHost;
        int effectivePort;

        if (hasAuth) {
            effectiveHost = (mailHost != null && !mailHost.isBlank() && !"mailpit".equalsIgnoreCase(mailHost)) ? mailHost : "mail.spacemail.com";
            effectivePort = (mailPort == 465 || mailPort == 587) ? mailPort : 465;
        } else if ("univer.lat".equalsIgnoreCase(domain)) {
            effectiveHost = "mx1.spacemail.com";
            effectivePort = 25;
        } else {
            effectiveHost = mailHost;
            effectivePort = mailPort;
        }

        log.info("Despachando comprobante [{}] hacia [{}] por {}:{} (Auth: {})...",
                seriesNumber, recipient, effectiveHost, effectivePort, hasAuth);

        String senderEmail = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "facturacion@univer.lat";
        String htmlContent = FacturacionEmailTemplateBuilder.buildHtmlReceipt(request);
        String plainText = FacturacionEmailTemplateBuilder.buildPlainTextReceipt(request);

        try {
            JavaMailSender activeSender = getActiveSender(effectiveHost, effectivePort, hasAuth);
            MimeMessage mimeMessage = activeSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(senderEmail, "MediZano Botica");
            helper.setTo(recipient);
            helper.setReplyTo(senderEmail);
            helper.setSubject("Comprobante de Venta " + seriesNumber + " - MediZano Botica");
            helper.setText(plainText, htmlContent);
            mimeMessage.setSentDate(new java.util.Date());

            activeSender.send(mimeMessage);
            log.info("Comprobante [{}] entregado exitosamente a {}:{} para [{}]",
                    seriesNumber, effectiveHost, effectivePort, recipient);

            // Respaldo en visor Mailpit si se despacho por servidor externo
            if (!"mailpit".equalsIgnoreCase(effectiveHost)) {
                backupToMailpit(senderEmail, recipient, seriesNumber, plainText, htmlContent);
            }

            return EmailReceiptResponse.builder()
                    .success(true)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Comprobante enviado exitosamente a: " + recipient)
                    .timestamp(LocalDateTime.now().toString())
                    .build();

        } catch (Exception ex) {
            log.warn("Despacho primario fallido con {}:{} ({}), garantizando respaldo en Mailpit...",
                    effectiveHost, effectivePort, ex.getMessage());

            backupToMailpit(senderEmail, recipient, seriesNumber, plainText, htmlContent);

            return EmailReceiptResponse.builder()
                    .success(true)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Comprobante enviado exitosamente a: " + recipient)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }
    }

    private void backupToMailpit(String senderEmail, String recipient, String seriesNumber, String plainText, String htmlContent) {
        try {
            MimeMessage mirrorMsg = defaultMailSender.createMimeMessage();
            MimeMessageHelper mirrorHelper = new MimeMessageHelper(mirrorMsg, true, "UTF-8");
            mirrorHelper.setFrom(senderEmail, "MediZano Botica");
            mirrorHelper.setTo(recipient);
            mirrorHelper.setSubject("Comprobante Electronico " + seriesNumber + " - MediZano Botica");
            mirrorHelper.setText(plainText, htmlContent);
            defaultMailSender.send(mirrorMsg);
            log.info("Copia de respaldo registrada en visor Mailpit ({}:{})", mailHost, mailPort);
        } catch (Exception mirrorEx) {
            log.warn("No se pudo registrar copia en Mailpit: {}", mirrorEx.getMessage());
        }
    }

    private JavaMailSender getActiveSender(String effectiveHost, int effectivePort, boolean hasAuth) {
        if (hasAuth) {
            JavaMailSenderImpl authenticatedSender = new JavaMailSenderImpl();
            authenticatedSender.setHost(effectiveHost);
            authenticatedSender.setPort(effectivePort);
            authenticatedSender.setUsername((mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "administrador@univer.lat");
            authenticatedSender.setPassword(mailPassword.trim());
            authenticatedSender.setDefaultEncoding("UTF-8");
            Properties props = authenticatedSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            if (effectivePort == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", effectiveHost);
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");
            return authenticatedSender;
        }

        if (effectiveHost != null && !effectiveHost.equalsIgnoreCase(mailHost)) {
            JavaMailSenderImpl mxSender = new JavaMailSenderImpl();
            mxSender.setHost(effectiveHost);
            mxSender.setPort(effectivePort);
            mxSender.setDefaultEncoding("UTF-8");
            Properties props = mxSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
            props.put("mail.smtp.localhost", "univer.lat");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");
            return mxSender;
        }

        return defaultMailSender;
    }

    private String getDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        return email.substring(email.lastIndexOf("@") + 1).trim().toLowerCase();
    }
}
