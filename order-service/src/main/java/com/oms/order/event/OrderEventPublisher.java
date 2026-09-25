package com.oms.order.event;

import com.oms.order.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * The single place order-service publishes order-events.
 *
 * This REPLACES the old synchronous Feign call to notification-service in the order
 * flows. The important behavioural difference:
 *
 *   Before (Feign): if notification-service was down, the notification was lost
 *                   forever (the call threw and we swallowed it).
 *   After  (Kafka): the event is durably stored in the topic. If notification-service
 *                   is down, the event waits; when the service comes back, it consumes
 *                   from its last committed offset and no notification is lost.
 *
 * Publishing is asynchronous and non-blocking — send() returns a CompletableFuture and
 * we attach a callback purely for logging. A failed publish never breaks the order:
 * the order is already committed in the database before we get here.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish an order event.
     *
     * KEY = userId. This is deliberate: Kafka routes all records with the same key to
     * the same partition, and ordering is guaranteed within a partition. So a user's
     * ORDER_PLACED is always consumed before their later ORDER_CANCELLED.
     */
    public void publish(OrderEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}: {}",
                                event.getEventType(), event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published {} for order {} -> partition {}, offset {}",
                                event.getEventType(), event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
