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
            diagnosticsService.recordSuccess(request.queryId(), response.confidenceScore());
            return response;
        } catch (RagGenerationException e) {
            logger.error("RAG pipeline failed for query {}: {}", request.queryId(), e.getMessage());
            diagnosticsService.recordFailure(request.queryId(), e.getErrorCategory());
            return createFallbackResponse(request.queryId());
        } catch (Exception e) {
            logger.error("Unexpected connection error for query {}: {}", request.queryId(), e.getMessage());
            diagnosticsService.recordFailure(request.queryId(), ErrorCategory.INTERNAL_CONNECTION_ERROR);
            return createFallbackResponse(request.queryId());
        }
    }

    private RagRetrievalResponse createFallbackResponse(String queryId) {
        return new RagRetrievalResponse(
                queryId,
                GenerationStatus.EMPTY_RETRIEVAL,
                null,
                0.0,
                List.of()
        );
    }
}