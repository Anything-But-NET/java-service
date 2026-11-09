package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.*;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Service
public class RagGenerationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RagGenerationOrchestrator.class);
    private static final String ESCALATED_REPLY_TEXT =
            "I couldn't find a confident answer in the knowledge base. I have created a support ticket for an expert to assist you.";

    private final RagHttpClient ragHttpClient;
    private final GenerationDiagnosticsService diagnosticsService;
    private final DocumentMetadataService documentMetadataService;
    private final UsersService usersService;

    public RagGenerationOrchestrator(RagHttpClient ragHttpClient,
                                     GenerationDiagnosticsService diagnosticsService,
                                     DocumentMetadataService documentMetadataService,
                                     UsersService usersService) {
        this.ragHttpClient = ragHttpClient;
        this.diagnosticsService = diagnosticsService;
        this.documentMetadataService = documentMetadataService;
        this.usersService = usersService;
    }

    public UserQuestionResponse generateUserQuestions(UserQuestionRequest request) {
        var userGroups = new AclPayload(usersService.getUserGroups(request.userEmail()));

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
        if (request.queryId() != null) {
            var existing = diagnosticsService.findByQueryId(request.queryId());
            if (existing.isPresent()) {
                var diag = existing.get();
                if (diag.getOutcome() == GenerationOutcome.SUCCESS) {
                    logger.warn("QueryId {} has already been successfully processed. Rejecting duplicate query.", request.queryId());
                    throw new RagGenerationException("Query has already been successfully processed", ErrorCategory.VALIDATION_ERROR);
                } else {
                    logger.warn("Retry detected for failed queryId: {}. Preventing duplicate processing.", request.queryId());
                    ErrorCategory category = diag.getErrorCategory() != null
                            ? diag.getErrorCategory()
                            : ErrorCategory.INTERNAL_ERROR;
                    throw new RagGenerationException("Request previously failed with: " + category, category);
                }
            }
        }

        try {
            RagRetrievalResponse response = ragHttpClient.generateAnswer(request);
            recordSuccessSafe(request.queryId(), response.confidenceScore());
            return response;
        } catch (RagGenerationException e) {
            logger.error("RAG pipeline failed for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), e.getErrorCategory());
            throw e;
        } catch (ResourceAccessException e) {
            ErrorCategory category = isTimeout(e) ? ErrorCategory.RETRIEVAL_TIMEOUT : ErrorCategory.INTERNAL_CONNECTION_ERROR;
            logger.error("RAG request resource access error for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), category);
            throw new RagGenerationException("RAG request failed: " + category, e, category);
        } catch (Exception e) {
            logger.error("Unexpected connection error for query {}", request.queryId(), e);
            recordFailureSafe(request.queryId(), ErrorCategory.INTERNAL_CONNECTION_ERROR);
            throw new RagGenerationException("Unexpected connection error", e, ErrorCategory.INTERNAL_CONNECTION_ERROR);
        }
    }

    private boolean isTimeout(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException ||
                cause instanceof HttpTimeoutException ||
                (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("timed out"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
