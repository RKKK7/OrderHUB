package com.oms.order.config;

import com.oms.order.event.OrderEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer configuration — turns Java OrderEvent objects into bytes on the wire.
 *
 * The two halves of a Kafka record are serialized separately:
 *   - KEY   -> String  (the userId)          -> StringSerializer
 *   - VALUE -> OrderEvent (JSON)             -> JsonSerializer
 *
 * Why we set ADD_TYPE_INFO_HEADERS = false:
 *   By default Spring's JsonSerializer stamps the fully-qualified Java class name
 *   (e.g. com.oms.order.event.OrderEvent) into a record header. The consumer in
 *   notification-service has its OWN class (com.oms.notification.event.OrderEvent),
 *   so if it trusted that header it would try to instantiate a class it doesn't have
 *   and fail. Turning the header off keeps the two services decoupled — they agree on
 *   the JSON *shape*, not on a shared Java class. This matches the project's existing
 *   "no shared library" principle.
 *
 * acks=all + idempotence=true:
 *   acks=all  -> the broker only acknowledges once all in-sync replicas have the
 *                record. Strongest durability (no silently-lost events).
 *   idempotence -> the producer safely retries without creating duplicates on the
 *                  broker side. Good default for reliable producers.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, OrderEvent> orderEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> orderEventKafkaTemplate() {
        return new KafkaTemplate<>(orderEventProducerFactory());
    }
}
