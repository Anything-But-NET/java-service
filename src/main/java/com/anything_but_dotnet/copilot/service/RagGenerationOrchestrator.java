package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
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
            return handleFailure(request.queryId(), e.getErrorCategory(), e.getMessage(), e);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                return handleFailure(request.queryId(), ErrorCategory.RETRIEVAL_TIMEOUT, "RAG service timeout", e);
            }
            throw e;
        } catch (Exception e) {
            return handleFailure(request.queryId(), ErrorCategory.INTERNAL_CONNECTION_ERROR, "Unexpected connection error", e);
        }
    }

    private RagRetrievalResponse handleFailure(String queryId, ErrorCategory category, String message, Exception e) {
        logger.error("RAG pipeline error for query {}: {}", queryId, message, e);
        recordFailureSafe(queryId, category);
        return createFallbackResponse(queryId);
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