package com.oms.notification.event;

import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
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
class StockEventListenerTest {

    @Mock
    private NotificationRepository notifRepo;

    @InjectMocks
    private StockEventListener listener;

    private StockEvent lowStockEvent() {
        return StockEvent.builder()
                .eventId("stock-evt-001")
                .eventType("LOW_STOCK_ALERT")
                .productId("prod-42")
                .productName("Wireless Mouse")
                .stockAfter(3)
                .threshold(5)
                .build();
    }

    @Test
    void onStockEvent_lowStockAlert_savesAdminBroadcastNotification() {
        StockEvent event = lowStockEvent();
        when(notifRepo.existsByEventId("stock-evt-001")).thenReturn(false);

        listener.onStockEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("ADMIN_BROADCAST");
        assertThat(saved.getTitle()).isEqualTo("Low stock: Wireless Mouse");
        assertThat(saved.getMessage()).contains("3").contains("5");
        assertThat(saved.getType()).isEqualTo("LOW_STOCK_ALERT");
        assertThat(saved.getLink()).isEqualTo("/admin/products");
    }

    @Test
    void onStockEvent_stockUpdated_ignored() {
        StockEvent event = StockEvent.builder()
                .eventId("stock-evt-002")
                .eventType("STOCK_UPDATED")
                .productId("prod-42")
                .productName("Wireless Mouse")
                .stockAfter(50)
                .build();

        listener.onStockEvent(event);

        verify(notifRepo, never()).save(any(Notification.class));
    }

    @Test
    void onStockEvent_duplicateLowStockAlert_skips() {
        StockEvent event = lowStockEvent();
        when(notifRepo.existsByEventId("stock-evt-001")).thenReturn(true);

        listener.onStockEvent(event);

        verify(notifRepo, never()).save(any(Notification.class));
    }

    @Test
    void onStockEvent_nullEvent_skips() {
        listener.onStockEvent(null);
        verify(notifRepo, never()).save(any(Notification.class));
    }

    @Test
    void onStockEvent_nullEventType_skips() {
        StockEvent event = StockEvent.builder().eventId("x").eventType(null).build();

        listener.onStockEvent(event);

        verify(notifRepo, never()).save(any(Notification.class));
    }
}
