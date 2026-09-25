package com.oms.notification.event;

import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes "stock-events" — a SECOND topic with a SEPARATE consumer group.
 *
 * This class exists mainly to demonstrate the multi-topic / multi-group side of Kafka:
 *   - different topic (stock-events, not order-events)
 *   - different consumer group ("stock-monitor-group") — completely independent offsets
 *     from notification-group, so the two never interfere.
 *
 * It reacts only to LOW_STOCK_ALERT (the others — RESERVED / RESTORED / UPDATED — are
 * logged at debug for visibility). On a low-stock alert it records a broadcast admin
 * notification.
 *
 * DESIGN NOTE: notification-service is a leaf node and deliberately does NOT know which
 * users are admins (that's order-service's data, and calling back would break the leaf
 * rule). So the alert is stored under a conventional "ADMIN_BROADCAST" channel and
 * logged. A production system would resolve real admin recipients via an admin registry
 * or a dedicated admin-notification topic. Kept simple on purpose.
 */
@Component
@Slf4j
public class StockEventListener {

    public static final String ADMIN_BROADCAST = "ADMIN_BROADCAST";

    private final NotificationRepository notifRepo;

    public StockEventListener(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    @KafkaListener(
            topics = "stock-events",
            groupId = "stock-monitor-group",
            containerFactory = "stockKafkaListenerContainerFactory")
    public void onStockEvent(StockEvent event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Received null/invalid stock event — skipping");
            return;
        }

        if (!"LOW_STOCK_ALERT".equals(event.getEventType())) {
            log.debug("Stock event {} for product {} (stockAfter={}) — no action",
                    event.getEventType(), event.getProductId(), event.getStockAfter());
            return;
        }

        // Idempotency on the alert's eventId
        if (event.getEventId() != null && notifRepo.existsByEventId(event.getEventId())) {
            log.info("Duplicate low-stock alert {} — skipping", event.getEventId());
            return;
        }

        log.warn("LOW STOCK: '{}' ({}) down to {} (threshold {})",
                event.getProductName(), event.getProductId(),
                event.getStockAfter(), event.getThreshold());

        Notification n = new Notification();
        n.setEventId(event.getEventId());
        n.setUserId(ADMIN_BROADCAST);
        n.setUserEmail("");
        n.setTitle("Low stock: " + event.getProductName());
        n.setMessage("Stock for '" + event.getProductName() + "' is down to "
                + event.getStockAfter() + " (threshold " + event.getThreshold() + "). Consider restocking.");
        n.setType("LOW_STOCK_ALERT");
        n.setLink("/admin/products");
        notifRepo.save(n);
    }
}
