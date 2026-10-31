package com.anything_but_dotnet.copilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class RagClientConfig {

    @Bean
    public RestClient ragRestClient(@Value("${rag.api.url:http://localhost:8000}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}