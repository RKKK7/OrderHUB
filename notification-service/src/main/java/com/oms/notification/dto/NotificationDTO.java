package com.oms.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDTO {
    private String id;
    private String userId;
    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private String link;
    private String createdAt;  // String, never Instant
}
