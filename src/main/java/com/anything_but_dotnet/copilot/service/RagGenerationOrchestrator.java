package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.*;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagGenerationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RagGenerationOrchestrator.class);
    private static final String ESCALATED_REPLY_TEXT =
            "I couldn't find a confident answer in the knowledge base. I have created a support ticket for an expert to assist you.";

    private final RagHttpClient ragHttpClient;
    private final GenerationDiagnosticsService diagnosticsService;
    private final DocumentMetadataService documentMetadataService;

    public RagGenerationOrchestrator(RagHttpClient ragHttpClient,
                                     GenerationDiagnosticsService diagnosticsService,
                                     DocumentMetadataService documentMetadataService) {
        this.ragHttpClient = ragHttpClient;
        this.diagnosticsService = diagnosticsService;
        this.documentMetadataService = documentMetadataService;
    }

    public UserQuestionResponse generateUserQuestions(UserQuestionRequest request) {
        // TODO: replace with actual ACL resolution service
        var userGroups = new AclPayload(List.of(request.userEmail()));

        var result = processUserQuery(new RagRetrievalRequest(request.queryId(), request.query(), userGroups));

        if (result.status() != GenerationStatus.ANSWER_GENERATED) {
            return new UserQuestionResponse(
                    request.queryId(),
                    QuestionStatus.ESCALATED_TO_EXPERT,
                    ESCALATED_REPLY_TEXT,
                    List.of()
            );
        }

        var enrichedCitations = documentMetadataService.enrichCitations(result.citations());

        return new UserQuestionResponse(
                request.queryId(),
                QuestionStatus.ANSWERED,
                result.answer(),
                enrichedCitations
        );
    }

    RagRetrievalResponse processUserQuery(RagRetrievalRequest request) {
        try {
            RagRetrievalResponse response = ragHttpClient.generateAnswer(request);
            recordSuccessSafe(request.queryId(), response.confidenceScore());
            return response;
        } catch (RagGenerationException e) {
            logger.error("RAG pipeline failed for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), e.getErrorCategory());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected connection error for query {}", request.queryId(), e);
            recordFailureSafe(request.queryId(), ErrorCategory.INTERNAL_CONNECTION_ERROR);
            throw new RagGenerationException("Unexpected connection error", e, ErrorCategory.INTERNAL_CONNECTION_ERROR);
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
}
