package com.oms.notification.controller;

import com.oms.notification.dto.ApiException;
import com.oms.notification.dto.NotificationDTO;
import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import com.oms.notification.service.NotificationMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifRepo;

    public NotificationController(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null) throw new ApiException(401, "Not authenticated");

        List<NotificationDTO> notifications = notifRepo
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30))
                .stream().map(NotificationMapper::toDTO).toList();
        long unread = notifRepo.countByUserIdAndIsReadFalse(userId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("notifications", notifications);
        res.put("unreadCount", unread);
        return res;
    }

    @PutMapping("/{id}/read")
    public Map<String, Object> markRead(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        notifRepo.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                notifRepo.save(n);
            }
        });
        return Map.of("message", "Marked as read");
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        List<Notification> unread = notifRepo.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notifRepo.saveAll(unread);
        return Map.of("message", "All marked as read", "count", unread.size());
    }
}
