package com.oms.notification.health;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator for Kafka connectivity.
 *
 * WHY THIS EXISTS: plain spring-kafka does NOT auto-register a health indicator (only
 * Spring Cloud Stream's binder does). Without this, /actuator/health would report UP
 * even when the broker is unreachable and every event publish/consume is failing.
 *
 * HOW SPRING FINDS IT: it's a @Component implementing HealthIndicator. At request time
 * the HealthEndpoint discovers every HealthContributor bean in the context and
 * aggregates them. The bean name "kafkaHealthIndicator" has its "HealthIndicator"
 * suffix stripped, so it appears in the JSON as "kafka".
 *
 * READINESS, NOT LIVENESS: this is wired into the readiness group (see
 * application.properties). A Kafka outage means "don't send me new traffic," not
 * "kill and restart the JVM" — so it must never gate liveness, or a broker blip would
 * cause pointless container restarts.
 *
 * A fresh short-lived AdminClient is created per check and closed via try-with-resources.
 * The describeCluster() call is bounded by a timeout so a hung broker can't hang health.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 3;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int brokerCount = cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

            if (brokerCount <= 0) {
                return Health.down()
                        .withDetail("reason", "No Kafka brokers available")
                        .build();
            }
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", brokerCount)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
