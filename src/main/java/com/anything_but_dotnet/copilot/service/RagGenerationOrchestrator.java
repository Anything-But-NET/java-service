package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.RagRetrievalRequest;
import com.anything_but_dotnet.copilot.dto.RagRetrievalResponse;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import org.springframework.stereotype.Service;

@Service
public class RagGenerationOrchestrator {

    private final RagHttpClient httpClient;
    private final GenerationDiagnosticsService diagnosticsService;

    public RagGenerationOrchestrator(RagHttpClient httpClient, GenerationDiagnosticsService diagnosticsService) {
        this.httpClient = httpClient;
        this.diagnosticsService = diagnosticsService;
    }

    public RagRetrievalResponse generateAnswer(RagRetrievalRequest request) {
        try {
            RagRetrievalResponse response = httpClient.generate(request);

            diagnosticsService.recordSuccess(request.query_id(), response.confidence_score());

            return response;

        } catch (RagGenerationException e) {
            diagnosticsService.recordFailure(request.query_id(), e.getErrorCategory());
            throw e;

        } catch (Exception e) {
            diagnosticsService.recordFailure(request.query_id(), "INTERNAL_CONNECTION_ERROR");
            throw e;
        }
    }
}
