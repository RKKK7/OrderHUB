package com.oms.notification.service;

import com.oms.notification.dto.NotificationDTO;
import com.oms.notification.model.Notification;

public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationDTO toDTO(Notification n) {
        if (n == null) return null;
        return NotificationDTO.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.isRead())
                .link(n.getLink())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : null)
                .build();
    }
}
