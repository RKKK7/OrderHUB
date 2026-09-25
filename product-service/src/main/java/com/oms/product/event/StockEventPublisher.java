package com.oms.product.event;

import com.oms.product.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes stock-events. This is a PURELY ADDITIVE change — it does not replace any
 * synchronous behaviour. The order flow's check-and-reserve / restore-stock stay exactly
 * as they were (synchronous Feign). We simply ALSO emit an event describing what changed,
 * so any interested consumer (notifications today; analytics/audit tomorrow) can react
 * without product-service knowing they exist. That is the core win of event-driven design.
 *
 * NOTE ON DELIVERY TIMING: these publish calls happen inside the @Transactional stock
 * methods. If a transaction were to roll back AFTER a publish, a spurious event could be
 * emitted (Kafka can't "unsend"). For this project that trade-off is acceptable. The
 * production-grade fix is the Transactional Outbox pattern (write the event to a DB table
 * in the same transaction, then a relay publishes it) — noted here as the next step.
 */
@Service
public class StockEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockEventPublisher.class);

    private final KafkaTemplate<String, StockEvent> kafkaTemplate;

    public StockEventPublisher(KafkaTemplate<String, StockEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Convenience builder + publish. quantityChanged is signed (- for reserve, + for restore). */
    public void publish(String eventType, String productId, String productName,
                        int quantityChanged, int stockAfter, int threshold) {
        StockEvent event = new StockEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now().toString(),
                productId,
                productName,
                quantityChanged,
                stockAfter,
                threshold
        );
        publish(event);
    }

    /**
     * KEY = productId -> all events for one product land on the same partition,
     * preserving per-product ordering.
     */
    public void publish(StockEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.STOCK_EVENTS_TOPIC, event.getProductId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for product {}: {}",
                                event.getEventType(), event.getProductId(), ex.getMessage());
                    } else {
                        log.info("Published {} for product {} -> partition {}, offset {}",
                                event.getEventType(), event.getProductId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
