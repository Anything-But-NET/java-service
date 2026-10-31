package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.GenerationError;
import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;

@Component
public class RagHttpClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RagHttpClient(RestClient ragRestClient, ObjectMapper objectMapper) {
        this.restClient = ragRestClient;
        this.objectMapper = objectMapper;
    }

    public RagRetrievalResponse generateAnswer(RagRetrievalRequest request) {
        return restClient.post()
                .uri("/api/v1/rag/generate")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    GenerationError error = parseError(res.getBody());
                    ErrorCategory category = (error != null && error.status() != null)
                            ? error.status()
                            : ErrorCategory.VALIDATION_ERROR;
                    throw new RagGenerationException("Client error during RAG generation", category);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    GenerationError error = parseError(res.getBody());
                    ErrorCategory category = (error != null && error.status() != null)
                            ? error.status()
                            : ErrorCategory.INTERNAL_ERROR;
                    throw new RagGenerationException("Server error during RAG generation", category);
                })
                .body(RagRetrievalResponse.class);
    }

    private GenerationError parseError(InputStream body) {
        try {
            return objectMapper.readValue(body, GenerationError.class);
        } catch (IOException e) {
            return null;
        }
    }
}