package com.oms.notification.controller;

import com.oms.notification.dto.ApiException;
import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationRepository notifRepo;

    @InjectMocks
    private NotificationController controller;

    private Notification sampleNotification(String id, String userId, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setUserEmail("user@test.com");
        n.setTitle("Test Title");
        n.setMessage("Test message");
        n.setType("ORDER_PLACED");
        n.setRead(read);
        n.setLink("/orders/1");
        return n;
    }

    @Test
    void list_returnsNotificationsAndUnreadCount() {
        String userId = "user-1";
        Notification n = sampleNotification("n1", userId, false);
        when(notifRepo.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(n));
        when(notifRepo.countByUserIdAndIsReadFalse(userId)).thenReturn(1L);

        Map<String, Object> result = controller.list(userId);

        assertThat(result).containsKey("notifications");
        assertThat(result.get("unreadCount")).isEqualTo(1L);
    }

    @Test
    void list_noUserId_throws401() {
        assertThatThrownBy(() -> controller.list(null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", 401);
    }

    @Test
    void markRead_setsReadTrueForOwnNotification() {
        String userId = "user-1";
        Notification n = sampleNotification("n1", userId, false);
        when(notifRepo.findById("n1")).thenReturn(Optional.of(n));

        Map<String, Object> result = controller.markRead(userId, "n1");

        assertThat(n.isRead()).isTrue();
        verify(notifRepo).save(n);
        assertThat(result.get("message")).isEqualTo("Marked as read");
    }

    @Test
    void markRead_differentUser_doesNotModify() {
        Notification n = sampleNotification("n1", "user-1", false);
        when(notifRepo.findById("n1")).thenReturn(Optional.of(n));

        controller.markRead("other-user", "n1");

        assertThat(n.isRead()).isFalse();
        verify(notifRepo, never()).save(any());
    }

    @Test
    void markRead_noUserId_throws401() {
        assertThatThrownBy(() -> controller.markRead(null, "n1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", 401);
    }

    @Test
    void markAllRead_marksAllUnreadAsRead() {
        String userId = "user-1";
        Notification n1 = sampleNotification("n1", userId, false);
        Notification n2 = sampleNotification("n2", userId, false);
        when(notifRepo.findByUserIdAndIsReadFalse(userId)).thenReturn(List.of(n1, n2));

        Map<String, Object> result = controller.markAllRead(userId);

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notifRepo).saveAll(List.of(n1, n2));
        assertThat(result.get("count")).isEqualTo(2);
    }

    @Test
    void markAllRead_noUserId_throws401() {
        assertThatThrownBy(() -> controller.markAllRead(null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", 401);
    }
}
