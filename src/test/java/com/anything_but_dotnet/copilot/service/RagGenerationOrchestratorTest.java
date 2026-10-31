package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagGenerationOrchestratorTest {

    @Mock
    private RagHttpClient httpClient;

    @Mock
    private GenerationDiagnosticsService diagnosticsService;

    @InjectMocks
    private RagGenerationOrchestrator orchestrator;

    private RagRetrievalRequest request;

    @BeforeEach
    void setUp() {
        request = new RagRetrievalRequest(
                "query-123",
                "What is the vacation policy?",
                new RagRetrievalRequest.AclPayload(List.of("HR"))
        );
    }

    @Test
    void generateAnswer_ShouldReturnResponse_AndLogSuccess() {
        RagRetrievalResponse mockResponse = new RagRetrievalResponse(
                "query-123", "ANSWER_GENERATED", "You have 20 days.", 0.95, List.of()
        );
        when(httpClient.generate(request)).thenReturn(mockResponse);

        RagRetrievalResponse actualResponse = orchestrator.generateAnswer(request);

        assertEquals(mockResponse, actualResponse);
        verify(diagnosticsService, times(1)).recordSuccess("query-123", 0.95);
        verify(diagnosticsService, never()).recordFailure(any(), any());
    }

    @Test
    void generateAnswer_ShouldThrowException_AndLogFailure_WhenRagFails() {
        RagGenerationException exception = new RagGenerationException("VECTOR_DB_UNAVAILABLE", "Connection refused");
        when(httpClient.generate(request)).thenThrow(exception);

        RagGenerationException thrown = assertThrows(RagGenerationException.class, () -> {
            orchestrator.generateAnswer(request);
        });

        assertEquals("VECTOR_DB_UNAVAILABLE", thrown.getErrorCategory());
        verify(diagnosticsService, times(1)).recordFailure("query-123", "VECTOR_DB_UNAVAILABLE");
        verify(diagnosticsService, never()).recordSuccess(any(), any());
    }

    @Test
    void generateAnswer_ShouldThrowException_AndLogInternalFailure_WhenUnknownErrorOccurs() {
        RuntimeException exception = new RuntimeException("Some weird network error");
        when(httpClient.generate(request)).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> {
            orchestrator.generateAnswer(request);
        });

        verify(diagnosticsService, times(1)).recordFailure("query-123", "INTERNAL_CONNECTION_ERROR");
        verify(diagnosticsService, never()).recordSuccess(any(), any());
    }
}