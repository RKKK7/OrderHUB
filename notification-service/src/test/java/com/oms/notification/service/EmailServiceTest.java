package com.oms.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Test
    void sendHtml_successfullySendsEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailService service = new EmailService(mailSender, "OMS", "oms@example.com");
        service.sendHtml("user@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendHtml_noFromAddress_skipsWithoutSending() {
        EmailService service = new EmailService(mailSender, "OMS", "");

        service.sendHtml("user@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendHtml_nullFromAddress_skipsWithoutSending() {
        EmailService service = new EmailService(mailSender, "OMS", null);

        service.sendHtml("user@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendHtml_exceptionThrown_catchesAndDoesNotPropagate() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailService service = new EmailService(mailSender, "OMS", "oms@example.com");

        // Should NOT throw
        service.sendHtml("user@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender).send(mimeMessage);
    }
}
