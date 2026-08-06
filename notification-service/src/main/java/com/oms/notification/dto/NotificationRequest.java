package com.oms.notification.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRequest {
    private String userId;
    private String userEmail;  // included so this service never calls back to order-service
    private String title;
    private String message;
    private String type;
    private String link;
}
