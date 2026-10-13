package com.oms.product.event;

import com.oms.product.config.KafkaTopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockEventPublisherTest {

    @Mock private KafkaTemplate<String, StockEvent> kafkaTemplate;

    @InjectMocks private StockEventPublisher publisher;

    @Test
    void publish_convenienceMethod_buildsEventAndSendsToKafka() {
        when(kafkaTemplate.send(anyString(), anyString(), any(StockEvent.class)))
                .thenReturn(new CompletableFuture<>());

        publisher.publish("STOCK_UPDATED", "prod-1", "Widget", -5, 45, 10);

        ArgumentCaptor<StockEvent> captor = ArgumentCaptor.forClass(StockEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.STOCK_EVENTS_TOPIC), eq("prod-1"), captor.capture());

        StockEvent event = captor.getValue();
        assertEquals("STOCK_UPDATED", event.getEventType());
        assertEquals("prod-1", event.getProductId());
        assertEquals("Widget", event.getProductName());
        assertEquals(-5, event.getQuantityChanged());
        assertEquals(45, event.getStockAfter());
        assertEquals(10, event.getThreshold());
        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void publish_directEvent_sendsWithProductIdAsKey() {
        StockEvent event = new StockEvent(
                "evt-1", "LOW_STOCK_ALERT", "2026-01-01T00:00:00Z",
                "prod-2", "Gadget", -3, 4, 5);
        when(kafkaTemplate.send(anyString(), anyString(), any(StockEvent.class)))
                .thenReturn(new CompletableFuture<>());

        publisher.publish(event);

        verify(kafkaTemplate).send(KafkaTopicConfig.STOCK_EVENTS_TOPIC, "prod-2", event);
    }
}
