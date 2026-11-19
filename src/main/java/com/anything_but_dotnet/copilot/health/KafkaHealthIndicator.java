package com.anything_but_dotnet.copilot.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED");

    @FunctionalInterface
    public interface ClusterBrokerProbe {
        int countActiveBrokers(Map<String, Object> adminConfigs, int timeoutMs) throws Exception;
    }

    private final KafkaAdmin kafkaAdmin;
    private final int timeoutMs;
    private final ClusterBrokerProbe brokerProbe;

    @Autowired
    public KafkaHealthIndicator(
            KafkaAdmin kafkaAdmin,
            @Value("${app.health.kafka.timeout-ms:1500}") int timeoutMs
    ) {
        this(kafkaAdmin, timeoutMs, KafkaHealthIndicator::probeClusterBrokers);
    }

    public KafkaHealthIndicator(
            KafkaAdmin kafkaAdmin,
            int timeoutMs,
            ClusterBrokerProbe brokerProbe
    ) {
        this.kafkaAdmin = kafkaAdmin;
        this.timeoutMs = timeoutMs;
        this.brokerProbe = brokerProbe;
    }

    private static int probeClusterBrokers(Map<String, Object> configs, int timeoutMs) throws Exception {
        AdminClient adminClient = AdminClient.create(configs);
        try {
            var clusterResult = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(timeoutMs));
            var nodes = clusterResult.nodes().get(timeoutMs, TimeUnit.MILLISECONDS);
            return nodes == null ? 0 : nodes.size();
        } finally {
            adminClient.close(Duration.ofMillis(timeoutMs));
        }
    }

    @Override
    public Health health() {
        Map<String, Object> configs = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);

        try {
            int brokerCount = brokerProbe.countActiveBrokers(configs, timeoutMs);
            if (brokerCount <= 0) {
                return Health.status(DEGRADED)
                        .withDetail("reason", "No active Kafka brokers found")
                        .build();
            }
            return Health.up()
                    .withDetail("brokerCount", brokerCount)
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.status(DEGRADED)
                    .withDetail("reason", "Kafka health check interrupted")
                    .build();
        } catch (Exception ex) {
            return Health.status(DEGRADED)
                    .withDetail("reason", "Kafka broker unreachable")
                    .build();
        }
    }
}
