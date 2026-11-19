package com.anything_but_dotnet.copilot.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Component("pythonService")
public class PythonServiceHealthIndicator implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED");

    private final RestClient healthRestClient;
    private final ObjectMapper objectMapper;

    private record RawHealthResponse(int statusCode, String body) {}

    @Autowired
    public PythonServiceHealthIndicator(
            @Value("${rag.api.url:http://localhost:8000}") String baseUrl,
            @Value("${rag.health.timeout.connect:1000}") int connectTimeoutMs,
            @Value("${rag.health.timeout.read:2500}") int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.healthRestClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    public PythonServiceHealthIndicator(RestClient healthRestClient, ObjectMapper objectMapper) {
        this.healthRestClient = healthRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Health health() {
        try {
            RawHealthResponse rawResponse = healthRestClient.get()
                    .uri("/health")
                    .exchange((request, response) -> {
                        try (var stream = response.getBody()) {
                            String body = stream == null
                                    ? ""
                                    : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            return new RawHealthResponse(response.getStatusCode().value(), body);
                        }
                    });

            if (rawResponse == null || rawResponse.body().isBlank()) {
                return Health.down()
                        .withDetail("reason", "Python RAG service returned empty health payload")
                        .build();
            }

            JsonNode root = objectMapper.readTree(rawResponse.body());
            String upstreamStatus = root.path("status").asText("").trim().toUpperCase();
            boolean is2xx = rawResponse.statusCode() >= 200 && rawResponse.statusCode() < 300;

            return switch (upstreamStatus) {
                case "UP", "OK" -> is2xx
                        ? Health.up().build()
                        : Health.down()
                                .withDetail("reason", "Python RAG service returned HTTP " + rawResponse.statusCode())
                                .build();
                case "DEGRADED" -> Health.status(DEGRADED)
                        .withDetail("reason", "Python RAG service is operating in DEGRADED mode")
                        .build();
                default -> Health.down()
                        .withDetail("reason", "Python RAG service reported status: " + (upstreamStatus.isEmpty() ? "DOWN" : upstreamStatus))
                        .build();
            };
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("reason", "Python RAG service is unreachable")
                    .build();
        }
    }
}
