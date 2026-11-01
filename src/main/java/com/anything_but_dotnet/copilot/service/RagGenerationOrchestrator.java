package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RagGenerationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RagGenerationOrchestrator.class);
    private final RagHttpClient ragHttpClient;
    private final GenerationDiagnosticsService diagnosticsService;

    public RagGenerationOrchestrator(RagHttpClient ragHttpClient, GenerationDiagnosticsService diagnosticsService) {
        this.ragHttpClient = ragHttpClient;
        this.diagnosticsService = diagnosticsService;
    }

    public RagRetrievalResponse processUserQuery(RagRetrievalRequest request) {
        try {
            RagRetrievalResponse response = ragHttpClient.generateAnswer(request);
            recordSuccessSafe(request.queryId(), response.confidenceScore());
            return response;
        } catch (RagGenerationException e) {
            logger.error("RAG pipeline failed for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), e.getErrorCategory());
            return createFallbackResponse(request.queryId());
        } catch (Exception e) {
            logger.error("Unexpected connection error for query {}", request.queryId(), e);
            recordFailureSafe(request.queryId(), ErrorCategory.INTERNAL_CONNECTION_ERROR);
            return createFallbackResponse(request.queryId());
        }
    }

    private void recordSuccessSafe(String queryId, Double confidenceScore) {
        try {
            diagnosticsService.recordSuccess(queryId, confidenceScore);
        } catch (Exception ex) {
            logger.warn("Failed to record success diagnostics for query {}", queryId, ex);
        }
    }

    private void recordFailureSafe(String queryId, ErrorCategory category) {
        try {
            diagnosticsService.recordFailure(queryId, category);
        } catch (Exception ex) {
            logger.warn("Failed to record failure diagnostics for query {}", queryId, ex);
        }
    }

    private RagRetrievalResponse createFallbackResponse(String queryId) {
        return new RagRetrievalResponse(
                queryId,
                GenerationStatus.ERROR,
                null,
                0.0,
                List.of()
        );
    }
}