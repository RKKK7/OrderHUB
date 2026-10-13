package com.oms.order.event;

import com.oms.order.config.KafkaTopicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);
    }

    private OrderEvent testEvent() {
        return OrderEvent.builder()
                .eventId("evt-1")
                .eventType("ORDER_PLACED")
                .timestamp("2026-01-01T00:00:00Z")
                .orderId("order-1")
                .userId("user-1")
                .userEmail("john@example.com")
                .totalAmount(new BigDecimal("200.00"))
                .shippingAddress("123 Main St")
                .title("Order Confirmed")
                .message("Your order has been confirmed")
                .link("/orders/order-1")
                .build();
    }

    @Test
    void publish_sendsToCorrectTopicWithUserIdAsKey() {
        OrderEvent event = testEvent();
        CompletableFuture<SendResult<String, OrderEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(KafkaTopicConfig.ORDER_EVENTS_TOPIC), eq("user-1"), eq(event)))
                .thenReturn(future);

        publisher.publish(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eq(event));

        assertEquals("order-events", topicCaptor.getValue());
        assertEquals("user-1", keyCaptor.getValue());
    }

    @Test
    void publish_doesNotThrowOnSendFailure() {
        OrderEvent event = testEvent();
        CompletableFuture<SendResult<String, OrderEvent>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEvent.class)))
                .thenReturn(future);

        // Simulate async failure
        future.completeExceptionally(new RuntimeException("Broker down"));

        assertDoesNotThrow(() -> publisher.publish(event));
    }
}
