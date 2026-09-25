package com.oms.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the "stock-events" topic that product-service owns and produces to.
 *
 * Partition key is productId (see StockEventPublisher), so all events for one product
 * stay ordered on the same partition — e.g. a STOCK_RESERVED is always seen before the
 * matching STOCK_RESTORED for that product.
 *
 * 3 partitions / 1 replica: same reasoning as order-events — parallelism for consumers,
 * single-broker dev cluster.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String STOCK_EVENTS_TOPIC = "stock-events";

    @Bean
    public NewTopic stockEventsTopic() {
        return TopicBuilder.name(STOCK_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
