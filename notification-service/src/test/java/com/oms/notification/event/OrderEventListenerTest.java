package com.oms.notification.event;

import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import com.oms.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private NotificationRepository notifRepo;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OrderEventListener listener;

    private OrderEvent sampleEvent() {
        return OrderEvent.builder()
                .eventId("evt-001")
                .eventType("ORDER_PLACED")
                .orderId("ord-123")
                .userId("user-1")
                .userEmail("user@example.com")
                .title("Order Placed")
                .message("Your order has been placed successfully.")
                .link("/orders/ord-123")
                .build();
    }

    @Test
    void onOrderEvent_savesNotificationAndSendsEmail() {
        OrderEvent event = sampleEvent();
        when(notifRepo.existsByEventId("evt-001")).thenReturn(false);

        listener.onOrderEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-001");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getTitle()).isEqualTo("Order Placed");
        assertThat(saved.getType()).isEqualTo("ORDER_PLACED");
        assertThat(saved.getLink()).isEqualTo("/orders/ord-123");

        verify(emailService).sendHtml(eq("user@example.com"), eq("OMS: Order Placed"), anyString());
    }

    @Test
    void onOrderEvent_duplicateEvent_skipsProcessing() {
        OrderEvent event = sampleEvent();
        when(notifRepo.existsByEventId("evt-001")).thenReturn(true);

        listener.onOrderEvent(event);

        verify(notifRepo, never()).save(any(Notification.class));
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void onOrderEvent_nullEvent_skips() {
        listener.onOrderEvent(null);

        verify(notifRepo, never()).existsByEventId(anyString());
        verify(notifRepo, never()).save(any(Notification.class));
    }

    @Test
    void onOrderEvent_nullEventId_skips() {
        OrderEvent event = sampleEvent();
        event.setEventId(null);

        listener.onOrderEvent(event);

        verify(notifRepo, never()).existsByEventId(anyString());
        verify(notifRepo, never()).save(any(Notification.class));
    }

    @Test
    void onOrderEvent_blankEmail_skipsEmailButSavesNotification() {
        OrderEvent event = sampleEvent();
        event.setUserEmail("   ");
        when(notifRepo.existsByEventId("evt-001")).thenReturn(false);

        listener.onOrderEvent(event);

        verify(notifRepo).save(any(Notification.class));
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void onOrderEvent_nullEmail_skipsEmailButSavesNotification() {
        OrderEvent event = sampleEvent();
        event.setUserEmail(null);
        when(notifRepo.existsByEventId("evt-001")).thenReturn(false);

        listener.onOrderEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(captor.capture());
        assertThat(captor.getValue().getUserEmail()).isEqualTo("");

        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void onOrderEvent_nullEventType_defaultsToSystem() {
        OrderEvent event = sampleEvent();
        event.setEventType(null);
        when(notifRepo.existsByEventId("evt-001")).thenReturn(false);

        listener.onOrderEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("SYSTEM");
    }
}
