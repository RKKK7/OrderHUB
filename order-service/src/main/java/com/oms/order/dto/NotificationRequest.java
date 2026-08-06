package com.oms.order.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRequest {
    private String userId;
    private String userEmail;  // sent so notification-service never calls back
    private String title;
    private String message;
    private String type;       // ORDER_PLACED | ORDER_CONFIRMED | ORDER_SHIPPED | ORDER_DELIVERED | ORDER_CANCELLED
    private String link;
}
