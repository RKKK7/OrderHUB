package com.oms.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topic this service OWNS and produces to.
 *
 * When the app starts, Spring's KafkaAdmin sees this NewTopic bean and creates the
 * topic on the broker if it doesn't already exist (with the partition/replica counts
 * below). This is why the producer service is the right place to declare topics —
 * the producer "owns" the topic's shape.
 *
 * PARTITIONS (3):
 *   A topic is split into partitions. Partitions are the unit of parallelism and
 *   ordering in Kafka. Messages with the same key always go to the same partition,
 *   and order is guaranteed *within* a partition (not across partitions).
 *   We publish with key = userId (see OrderEventPublisher), so all events for one
 *   user land on one partition and are consumed in the order they happened.
 *   3 partitions means up to 3 consumer instances in a group can work in parallel.
 *
 * REPLICAS (1):
 *   How many brokers keep a copy of each partition. We run a single-broker dev
 *   cluster, so 1 is the only valid value. In production this would be 3.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_EVENTS_TOPIC = "order-events";

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
