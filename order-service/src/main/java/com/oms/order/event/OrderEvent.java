package com.oms.order.event;

import lombok.*;

import java.math.BigDecimal;

/**
 * OrderEvent — the message published to the "order-events" Kafka topic.
 *
 * Every order lifecycle change (placed, confirmed, shipped, delivered, cancelled)
 * becomes one of these events. This is the payload that flows over Kafka INSTEAD of
 * the old synchronous Feign call to notification-service.
 *
 * Design notes (why the fields are what they are):
 *  - eventId       : a unique id per event. Consumers use it for IDEMPOTENCY —
 *                    if the same event is delivered twice (Kafka guarantees
 *                    at-least-once delivery, not exactly-once), the consumer can
 *                    detect the duplicate and skip it.
 *  - eventType     : ORDER_PLACED | ORDER_CONFIRMED | ORDER_SHIPPED |
 *                    ORDER_DELIVERED | ORDER_CANCELLED
 *  - userEmail     : DENORMALIZED into the event so the consumer never has to call
 *                    back to order-service. This preserves the "notification-service
 *                    is a leaf node" rule the project already follows with Feign.
 *  - title/message : the human-readable copy. Kept here (produced by the service that
 *                    owns the business logic) so the consumer stays "dumb" — it just
 *                    persists and emails what it is told.
 *  - timestamp     : ISO-8601 String, never Instant — matches the project-wide rule
 *                    that all DTO timestamps are Strings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

    private String eventId;
    private String eventType;
    private String timestamp;

    private String orderId;
    private String userId;
    private String userEmail;

    private BigDecimal totalAmount;
    private String shippingAddress;

    // Presentation copy for the notification/email
    private String title;
    private String message;
    private String link;
}
