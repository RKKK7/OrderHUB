package com.oms.notification.controller;

import com.oms.notification.dto.ApiException;
import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationRepository notifRepo;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notifRepo);
    }

    private Notification sample(String id, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId("user-1");
        n.setUserEmail("ravi@test.com");
        n.setTitle("Order Update");
        n.setMessage("Your order shipped");
        n.setType("ORDER_SHIPPED");
        n.setRead(read);
        return n;
    }

    @Test
    void list_returnsNotificationsAndUnreadCount() {
        when(notifRepo.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(List.of(sample("n1", false), sample("n2", true)));
        when(notifRepo.countByUserIdAndIsReadFalse("user-1")).thenReturn(1L);

        Map<String, Object> result = controller.list("user-1");

        assertEquals(1L, result.get("unreadCount"));
        assertEquals(2, ((List<?>) result.get("notifications")).size());
    }

    @Test
    void list_withoutAuth_throws401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.list(null));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void markRead_updatesCorrectNotification() {
        Notification n = sample("n1", false);
        when(notifRepo.findById("n1")).thenReturn(Optional.of(n));

        controller.markRead("user-1", "n1");

        assertTrue(n.isRead());
        verify(notifRepo, times(1)).save(n);
    }

    @Test
    void markRead_wrongUser_doesNotUpdate() {
        Notification n = sample("n1", false);
        when(notifRepo.findById("n1")).thenReturn(Optional.of(n));

        controller.markRead("other-user", "n1");

        assertFalse(n.isRead(), "Should not mark read for a different user's notification");
        verify(notifRepo, never()).save(any());
    }

    @Test
    void markRead_withoutAuth_throws401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.markRead(null, "n1"));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void markAllRead_updatesAllUnreadForUser() {
        Notification n1 = sample("n1", false);
        Notification n2 = sample("n2", false);
        when(notifRepo.findByUserIdAndIsReadFalse("user-1")).thenReturn(List.of(n1, n2));

        Map<String, Object> result = controller.markAllRead("user-1");

        assertTrue(n1.isRead());
        assertTrue(n2.isRead());
        verify(notifRepo, times(1)).saveAll(List.of(n1, n2));
        assertEquals(2, result.get("count"));
    }

    @Test
    void markAllRead_withoutAuth_throws401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.markAllRead(null));
        assertEquals(401, ex.getStatus());
    }
}
