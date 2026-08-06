package com.oms.notification.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromName;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from-name}") String fromName,
                        @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromName = fromName;
        this.fromAddress = fromAddress;
    }

    /**
     * Send an HTML email. Failures are logged but NEVER propagated —
     * a failed email must never break an order flow.
     */
    public void sendHtml(String to, String subject, String html) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.info("[EMAIL] (disabled — no EMAIL_USER configured) would send to {}: {}", to, subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("[EMAIL] Sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send to {}: {}", to, e.getMessage());
        }
    }
}
