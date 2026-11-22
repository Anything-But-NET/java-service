package com.anything_but_dotnet.copilot.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointWebExtension;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.kafka.core.KafkaAdmin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HealthIndicatorsTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startStubRagServer(int statusCode, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void pythonServiceHealthIndicatorReportsUpWhenRagReturnsUp() throws Exception {
        String baseUrl = startStubRagServer(200, "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}");
        var indicator = new PythonServiceHealthIndicator(baseUrl, 1000, 1000, new ObjectMapper());

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void pythonServiceHealthIndicatorReportsDegradedWhenRagReturnsDegraded() throws Exception {
        String baseUrl = startStubRagServer(200, "{\"status\":\"DEGRADED\",\"components\":{\"kafka\":{\"status\":\"DOWN\"}}}");
        var indicator = new PythonServiceHealthIndicator(baseUrl, 1000, 1000, new ObjectMapper());

        Health health = indicator.health();

        assertEquals(PythonServiceHealthIndicator.DEGRADED, health.getStatus());
    }

    @Test
    void pythonServiceHealthIndicatorReportsDownWhenRagReturns503Down() throws Exception {
        String baseUrl = startStubRagServer(503, "{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"DOWN\"}}}");
        var indicator = new PythonServiceHealthIndicator(baseUrl, 1000, 1000, new ObjectMapper());

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void pythonServiceHealthIndicatorReportsDownWhenUnreachableWithoutLeakingUrl() {
        var indicator = new PythonServiceHealthIndicator("http://127.0.0.1:1", 200, 200, new ObjectMapper());

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Python RAG service is unreachable", health.getDetails().get("reason"));
    }

    @Test
    void kafkaHealthIndicatorReportsUpWhenClusterHasActiveBrokers() {
        KafkaAdmin kafkaAdmin = new KafkaAdmin(Map.of("bootstrap.servers", "localhost:9092"));
        var indicator = new KafkaHealthIndicator(kafkaAdmin, 1000, (configs, timeout) -> 1);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("brokerCount"));
    }

    @Test
    void kafkaHealthIndicatorReportsDegradedWhenNoActiveBrokersFound() {
        KafkaAdmin kafkaAdmin = new KafkaAdmin(Map.of("bootstrap.servers", "localhost:9092"));
        var indicator = new KafkaHealthIndicator(kafkaAdmin, 1000, (configs, timeout) -> 0);

        Health health = indicator.health();

        assertEquals(KafkaHealthIndicator.DEGRADED, health.getStatus());
        assertEquals("No active Kafka brokers found", health.getDetails().get("reason"));
    }

    @Test
    void kafkaHealthIndicatorReportsDegradedWithoutLeakingSecretsOnFailure() {
        String secretConfig = "sasl.jaas.config=SuperSecretKafkaPassword123";
        KafkaAdmin kafkaAdmin = new KafkaAdmin(Map.of("bootstrap.servers", "localhost:9092"));
        var indicator = new KafkaHealthIndicator(kafkaAdmin, 1000, (configs, timeout) -> {
            throw new RuntimeException(secretConfig);
        });

        Health health = indicator.health();

        assertEquals(KafkaHealthIndicator.DEGRADED, health.getStatus());
        assertFalse(health.getDetails().toString().contains("SuperSecretKafkaPassword123"));
    }

    @Test
    void actuatorHealthWebExtensionAggregatesDegradedAndDownHttpCodes() {
        var aggregator = StatusAggregator.of(List.of("DOWN", "OUT_OF_SERVICE", "DEGRADED", "UP", "UNKNOWN"));
        HttpCodeStatusMapper statusMapper = HttpCodeStatusMapper.of(Map.of(
                "DOWN", 503,
                "OUT_OF_SERVICE", 503,
                "DEGRADED", 200,
                "UP", 200
        ));
        HealthEndpointGroup primaryGroup = new HealthEndpointGroup() {
            @Override
            public boolean isMember(String name) {
                return true;
            }

            @Override
            public boolean showComponents(SecurityContext securityContext) {
                return true;
            }

            @Override
            public boolean showDetails(SecurityContext securityContext) {
                return true;
            }

            @Override
            public StatusAggregator getStatusAggregator() {
                return aggregator;
            }

            @Override
            public HttpCodeStatusMapper getHttpCodeStatusMapper() {
                return statusMapper;
            }

            @Override
            public AdditionalHealthEndpointPath getAdditionalPath() {
                return null;
            }
        };
        var groups = HealthEndpointGroups.of(primaryGroup, Map.of());

        var degradedRegistry = new DefaultHealthContributorRegistry();
        degradedRegistry.registerContributor("db", (HealthIndicator) () -> Health.up().withDetail("database", "PostgreSQL").build());
        degradedRegistry.registerContributor("kafka", (HealthIndicator) () -> Health.status(KafkaHealthIndicator.DEGRADED)
                .withDetail("reason", "Kafka broker unreachable")
                .build());
        degradedRegistry.registerContributor("pythonService", (HealthIndicator) () -> Health.up().build());

        var degradedExtension = new HealthEndpointWebExtension(degradedRegistry, null, groups, null);
        var degradedResponse = degradedExtension.health(ApiVersion.V3, null, SecurityContext.NONE);
        assertEquals(200, degradedResponse.getStatus());
        assertEquals(KafkaHealthIndicator.DEGRADED, degradedResponse.getBody().getStatus());

        var downRegistry = new DefaultHealthContributorRegistry();
        downRegistry.registerContributor("db", (HealthIndicator) () -> Health.down().withDetail("reason", "DB down").build());
        downRegistry.registerContributor("kafka", (HealthIndicator) () -> Health.status(KafkaHealthIndicator.DEGRADED).build());
        downRegistry.registerContributor("pythonService", (HealthIndicator) () -> Health.up().build());

        var downExtension = new HealthEndpointWebExtension(downRegistry, null, groups, null);
        var downResponse = downExtension.health(ApiVersion.V3, null, SecurityContext.NONE);
        assertEquals(503, downResponse.getStatus());
        assertEquals(Status.DOWN, downResponse.getBody().getStatus());
    }
}
