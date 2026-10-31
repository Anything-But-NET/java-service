package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.GenerationError;
import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RagHttpClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RagHttpClient(
            @Value("${rag.service.url:http://localhost:8000}") String ragUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(ragUrl).build();
        this.objectMapper = objectMapper;
    }

    public RagRetrievalResponse generate(RagRetrievalRequest request) {
        return restClient.post()
                .uri("/api/v1/rag/generate")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    GenerationError errorBody = objectMapper.readValue(res.getBody(), GenerationError.class);
                    throw new RagGenerationException(errorBody.status(), errorBody.message());
                })
                .body(RagRetrievalResponse.class);
    }
}