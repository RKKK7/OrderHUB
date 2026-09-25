package com.oms.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "userId"),
        @Index(name = "idx_notif_read", columnList = "userId, isRead")
})
@Getter @Setter @NoArgsConstructor
public class Notification {

    @Id
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    /**
     * The Kafka eventId that produced this notification (null for notifications created
     * via the internal REST endpoint). Used by consumers for IDEMPOTENCY — if the same
     * event is delivered twice, we detect it via existsByEventId and skip the duplicate.
     */
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private String type = "SYSTEM";  // ORDER_PLACED | ORDER_CONFIRMED | ORDER_SHIPPED | ORDER_DELIVERED | ORDER_CANCELLED

    @Column(nullable = false)
    private boolean isRead = false;

    private String link = "";

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    public void ensureId() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}
