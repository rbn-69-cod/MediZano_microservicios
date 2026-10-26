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
            log.warn("Solicitud de envio de comprobante cancelada: No se proporciono correo electronico.");
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
        String effectiveHost = hasAuth ? "mail.spacemail.com" : resolveMxHost(domain);
        int effectivePort = hasAuth ? 587 : 25;

        if (effectiveHost == null || effectiveHost.isBlank()) {
            effectiveHost = mailHost;
            effectivePort = mailPort;
        }

        log.info("Iniciando despacho de comprobante [{}] hacia buzon [{}] a traves de {}:{} (Auth: {})...",
                seriesNumber, recipient, effectiveHost, effectivePort, hasAuth);

        String senderEmail = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "facturacion@univer.lat";
        String htmlContent = FacturacionEmailTemplateBuilder.buildHtmlReceipt(request);
        String plainText = FacturacionEmailTemplateBuilder.buildPlainTextReceipt(request);

        try {
            JavaMailSender activeSender = getActiveSender(recipient, effectiveHost, effectivePort);
            MimeMessage mimeMessage = activeSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(senderEmail, "MediZano Botica");
            helper.setTo(recipient);
            helper.setReplyTo(senderEmail);
            helper.setSubject("Comprobante de Venta " + seriesNumber + " - MediZano Botica");
            helper.setText(plainText, htmlContent);
            mimeMessage.setSentDate(new java.util.Date());

            activeSender.send(mimeMessage);

            log.info("Comprobante [{}] entregado exitosamente a {}:{} (250 OK) para [{}]",
                    seriesNumber, effectiveHost, effectivePort, recipient);

            // Registro de copia en visor Mailpit si se despacho por fuera de el
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
            log.error("Fallo durante el despacho SMTP con {}:{} para [{}]: {}",
                    effectiveHost, effectivePort, recipient, ex.getMessage(), ex);

            // Siempre respaldar copia en Mailpit para que el comprobante nunca se pierda
            backupToMailpit(senderEmail, recipient, seriesNumber, plainText, htmlContent);

            String errorDetail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            String userFriendlyMessage;

            if (errorDetail.contains("550-5.7.1") || errorDetail.contains("not authorized") || errorDetail.contains("Relay Access Denied")) {
                userFriendlyMessage = "Servidor " + domain + " rechazo conexion directa por politica de IP residencial (550). Para entrega directa en Gmail/Hotmail configure SPRING_MAIL_PASSWORD en .env. Copia guardada en Mailpit (http://localhost:8025)";
            } else if (errorDetail.contains("timed out") || errorDetail.contains("Connection refused")) {
                userFriendlyMessage = "Puerto 25 bloqueado por el proveedor de internet hacia " + domain + ". Configure relay autenticado en .env. Copia guardada en Mailpit (http://localhost:8025)";
            } else {
                userFriendlyMessage = "Error al enviar correo: " + errorDetail + ". Copia disponible en Mailpit (http://localhost:8025)";
            }

            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message(userFriendlyMessage)
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

    private JavaMailSender getActiveSender(String recipient, String effectiveHost, int effectivePort) {
        // Opcion 1: Servidor autenticado Spacemail si se provee contrasena (100% firmado DKIM, SPF Pass)
        if (mailPassword != null && !mailPassword.isBlank()) {
            JavaMailSenderImpl authenticatedSender = new JavaMailSenderImpl();
            String host = (mailHost != null && !mailHost.isBlank() && !"mailpit".equalsIgnoreCase(mailHost)) ? mailHost : "mail.spacemail.com";
            int port = (mailPort == 465 || mailPort == 587) ? mailPort : 465;
            authenticatedSender.setHost(host);
            authenticatedSender.setPort(port);
            authenticatedSender.setUsername((mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "administrador@univer.lat");
            authenticatedSender.setPassword(mailPassword.trim());
            authenticatedSender.setDefaultEncoding("UTF-8");
            Properties props = authenticatedSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            if (port == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", host);
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "8000");
            props.put("mail.smtp.writetimeout", "8000");
            return authenticatedSender;
        }

        // Opcion 2: Entrega directa al servidor MX oficial del destinatario (Gmail, Hotmail, Univer.lat, etc.)
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

        // Opcion 3: Mailpit local / servidor configurado por defecto
        return defaultMailSender;
    }

    private String getDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        return email.substring(email.lastIndexOf("@") + 1).trim().toLowerCase();
    }

    private String resolveMxHost(String domain) {
        if (domain == null || domain.isBlank()) return null;
        if ("univer.lat".equalsIgnoreCase(domain)) return "mx1.spacemail.com";
        if ("gmail.com".equalsIgnoreCase(domain)) return "gmail-smtp-in.l.google.com";
        if ("hotmail.com".equalsIgnoreCase(domain) || "outlook.com".equalsIgnoreCase(domain) || "live.com".equalsIgnoreCase(domain)) {
            return "hotmail-com.olc.protection.outlook.com";
        }
        if ("yahoo.com".equalsIgnoreCase(domain)) return "mta5.am0.yahoodns.net";

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ictx = new InitialDirContext(env);
            Attributes attrs = ictx.getAttributes(domain, new String[] { "MX" });
            Attribute attr = attrs.get("MX");
            if (attr == null || attr.size() == 0) {
                return null;
            }
            String bestMx = null;
            int lowestPref = Integer.MAX_VALUE;
            for (int i = 0; i < attr.size(); i++) {
                String[] parts = ((String) attr.get(i)).split("\\s+");
                int pref = Integer.parseInt(parts[0]);
                String host = parts[1].endsWith(".") ? parts[1].substring(0, parts[1].length() - 1) : parts[1];
                if (pref < lowestPref) {
                    lowestPref = pref;
                    bestMx = host;
                }
            }
            return bestMx;
        } catch (Exception e) {
            log.warn("No se pudo resolver registro MX para el dominio {}: {}", domain, e.getMessage());
            return null;
        }
    }
}
