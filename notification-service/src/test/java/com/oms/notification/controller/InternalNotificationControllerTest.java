package com.oms.notification.controller;

import com.oms.notification.dto.NotificationRequest;
import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import com.oms.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalNotificationControllerTest {

    @Mock private NotificationRepository notifRepo;
    @Mock private EmailService emailService;

    private InternalNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalNotificationController(notifRepo, emailService);
    }

    private void stubSave() {
        when(notifRepo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId("notif-1");
            return n;
        });
    }

    @Test
    void send_withEmail_savesNotificationAndSendsEmail() {
        stubSave();
        NotificationRequest req = NotificationRequest.builder()
                .userId("user-1").userEmail("ravi@test.com")
                .title("Order Confirmed").message("Your order is confirmed")
                .type("ORDER_CONFIRMED").link("/orders/abc")
                .build();

        Map<String, Object> result = controller.send(req);

        assertEquals("notif-1", result.get("id"));
        assertEquals("Notification sent", result.get("message"));
        verify(notifRepo, times(1)).save(any(Notification.class));
        verify(emailService, times(1)).sendHtml(eq("ravi@test.com"), contains("Order Confirmed"), any());
    }

    @Test
    void send_withoutEmail_savesButDoesNotSendEmail() {
        stubSave();
        NotificationRequest req = NotificationRequest.builder()
                .userId("user-1").userEmail(null)
                .title("Update").message("Something happened")
                .type("SYSTEM").build();

        controller.send(req);

        verify(notifRepo, times(1)).save(any(Notification.class));
        verify(emailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void send_withBlankEmail_doesNotSendEmail() {
        stubSave();
        NotificationRequest req = NotificationRequest.builder()
                .userId("user-1").userEmail("   ")
                .title("Update").message("Something happened")
                .type("SYSTEM").build();

        controller.send(req);

        verify(emailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void send_withNullType_defaultsToSystem() {
        stubSave();
        NotificationRequest req = NotificationRequest.builder()
                .userId("user-1").userEmail(null)
                .title("Title").message("Msg")
                .type(null).build();

        controller.send(req);

        verify(notifRepo).save(argThat(n -> "SYSTEM".equals(n.getType())));
    }

    @Test
    void send_dbFailure_returnsErrorMap() {
        when(notifRepo.save(any())).thenThrow(new RuntimeException("DB down"));
        NotificationRequest req = NotificationRequest.builder()
                .userId("user-1").title("Title").message("Msg").type("SYSTEM").build();

        Map<String, Object> result = controller.send(req);

        assertEquals("DB down", result.get("error"));
        verify(emailService, never()).sendHtml(any(), any(), any());
    }
}
