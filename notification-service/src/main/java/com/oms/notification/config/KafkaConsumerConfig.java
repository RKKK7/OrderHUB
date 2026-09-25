package com.oms.notification.config;

import com.oms.notification.event.OrderEvent;
import com.oms.notification.event.StockEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer configuration. notification-service consumes TWO different event types from
 * two topics, so we build TWO typed listener container factories — one per event class.
 * Each @KafkaListener then points at the factory that knows how to deserialize its type.
 *
 * KEY concepts wired here:
 *
 *  ConsumerFactory        : knows how to create Kafka consumers (bootstrap servers,
 *                           deserializers, offset-reset policy).
 *  ListenerContainerFactory: Spring wraps consumers in "listener containers" that poll
 *                           the broker and invoke your @KafkaListener method per record.
 *  AUTO_OFFSET_RESET=earliest: if this consumer group has no committed offset yet
 *                           (brand new group), start from the beginning of the topic so
 *                           no existing events are missed on first run.
 *  ErrorHandlingDeserializer: wraps the JSON deserializer so a single malformed message
 *                           (a "poison pill") is handled gracefully instead of crashing
 *                           the whole consumer in a tight retry loop.
 *  setUseTypeHeaders(false): the producer stripped type headers, so we tell the
 *                           deserializer to just use the target class we give it.
 *
 * Consumer GROUPS are set on the @KafkaListener itself (groupId=...), not here — see
 * OrderEventListener and StockEventListener.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Shared base consumer properties (group id is applied per-listener). */
    private Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    /** Build a JSON consumer factory bound to a specific event class. */
    private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> targetType) {
        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType);
        jsonDeserializer.addTrustedPackages("*");     // internal services; type is fixed below anyway
        jsonDeserializer.setUseTypeHeaders(false);   // producer sends no type header -> use targetType

        return new DefaultKafkaConsumerFactory<>(
                baseProps(),
                new StringDeserializer(),                                  // key
                new ErrorHandlingDeserializer<>(jsonDeserializer)          // value (safe)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(OrderEvent.class));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockEvent> stockKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(StockEvent.class));
        return factory;
    }
}
