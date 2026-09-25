package com.oms.notification.event;

import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import com.oms.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes "order-events" and turns each one into an in-app notification + email.
 *
 * This is the async replacement for the old Feign POST /internal/notifications/send.
 * The behaviour a user sees is identical; the resilience is better (events survive
 * this service being down).
 *
 * CONSUMER GROUP: "notification-group".
 *   Kafka distributes the topic's 3 partitions across all instances that share this
 *   group id. Run one instance -> it gets all 3 partitions. Run three -> one partition
 *   each (automatic rebalancing). Every message is processed by exactly one member of
 *   the group. A DIFFERENT group id would get its own independent copy of every message.
 *
 * IDEMPOTENCY: Kafka guarantees at-least-once delivery, so the same event can arrive
 * twice (e.g. after a rebalance before the offset was committed). We guard against
 * creating duplicate notifications by checking the eventId first.
 */
@Component
@Slf4j
public class OrderEventListener {

    private final NotificationRepository notifRepo;
    private final EmailService emailService;

    public OrderEventListener(NotificationRepository notifRepo, EmailService emailService) {
        this.notifRepo = notifRepo;
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "notification-group",
            containerFactory = "orderKafkaListenerContainerFactory")
    public void onOrderEvent(OrderEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("Received null/invalid order event — skipping");
            return;
        }

        // Idempotency: skip if we've already processed this exact event
        if (notifRepo.existsByEventId(event.getEventId())) {
            log.info("Duplicate order event {} ({}) — already processed, skipping",
                    event.getEventId(), event.getEventType());
            return;
        }

        log.info("Consuming {} for order {} (user {})",
                event.getEventType(), event.getOrderId(), event.getUserId());

        // 1. Persist the in-app notification
        Notification n = new Notification();
        n.setEventId(event.getEventId());
        n.setUserId(event.getUserId());
        n.setUserEmail(event.getUserEmail() != null ? event.getUserEmail() : "");
        n.setTitle(event.getTitle());
        n.setMessage(event.getMessage());
        n.setType(event.getEventType() != null ? event.getEventType() : "SYSTEM");
        n.setLink(event.getLink() != null ? event.getLink() : "");
        notifRepo.save(n);

        // 2. Send the email (failures are logged inside EmailService, never thrown)
        if (event.getUserEmail() != null && !event.getUserEmail().isBlank()) {
            emailService.sendHtml(event.getUserEmail(), "OMS: " + event.getTitle(),
                    buildEmailHtml(event));
        }
    }

    private String buildEmailHtml(OrderEvent event) {
        String button = (event.getLink() != null && !event.getLink().isBlank())
                ? "<a href=\"http://localhost:5173" + event.getLink() + "\" style=\"display:inline-block;"
                  + "margin-top:12px;background:#2563eb;color:#fff;padding:10px 20px;border-radius:6px;"
                  + "text-decoration:none;font-weight:600\">View Details</a>"
                : "";
        return """
            <div style="font-family:sans-serif;max-width:500px;margin:0 auto;padding:20px">
                <h2 style="color:#2563eb">Order Management System</h2>
                <h3>%s</h3>
                <p style="color:#374151;line-height:1.6">%s</p>
                %s
                <hr style="margin-top:24px;border:none;border-top:1px solid #e5e7eb"/>
                <p style="color:#9ca3af;font-size:12px">Order Management System</p>
            </div>
            """.formatted(event.getTitle(), event.getMessage(), button);
    }
}
