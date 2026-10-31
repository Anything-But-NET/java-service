package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RagGenerationOrchestratorTest {

    private RagHttpClient httpClient;
    private GenerationDiagnosticsService diagnosticsService;
    private RagGenerationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        httpClient = mock(RagHttpClient.class);
        diagnosticsService = mock(GenerationDiagnosticsService.class);
        orchestrator = new RagGenerationOrchestrator(httpClient, diagnosticsService);
    }

    @Test
    void shouldReturnResponseOnSuccess() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-1", "test", null);
        RagRetrievalResponse mockResponse = new RagRetrievalResponse(
                "q-1", GenerationStatus.ANSWER_GENERATED, "answer", 0.9, List.of()
        );

        when(httpClient.generateAnswer(request)).thenReturn(mockResponse);

        RagRetrievalResponse response = orchestrator.processUserQuery(request);

        assertEquals("q-1", response.queryId());
        assertEquals(GenerationStatus.ANSWER_GENERATED, response.status());
        verify(diagnosticsService).recordSuccess("q-1", 0.9);
    }

    @Test
    void shouldReturnFallbackOnException() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-2", "test", null);
        when(httpClient.generateAnswer(request)).thenThrow(
                new RagGenerationException("Error", ErrorCategory.LLM_PROVIDER_ERROR)
        );

        RagRetrievalResponse response = orchestrator.processUserQuery(request);

        assertEquals(GenerationStatus.EMPTY_RETRIEVAL, response.status());
        verify(diagnosticsService).recordFailure("q-2", ErrorCategory.LLM_PROVIDER_ERROR);
    }
}