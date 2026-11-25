package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.*;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class RagGenerationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RagGenerationOrchestrator.class);
    private static final String ESCALATED_REPLY_TEXT =
            "I couldn't find a confident answer in the knowledge base. Please confirm if you would like to send your question to an expert for review.";

    private final RagHttpClient ragHttpClient;
    private final GenerationDiagnosticsService diagnosticsService;
    private final DocumentMetadataService documentMetadataService;
    private final UsersService usersService;
    private final EscalationService escalationService;

    public RagGenerationOrchestrator(RagHttpClient ragHttpClient,
                                     GenerationDiagnosticsService diagnosticsService,
                                     DocumentMetadataService documentMetadataService,
                                     UsersService usersService,
                                     EscalationService escalationService) {
        this.ragHttpClient = ragHttpClient;
        this.diagnosticsService = diagnosticsService;
        this.documentMetadataService = documentMetadataService;
        this.usersService = usersService;
        this.escalationService = escalationService;
    }

    public UserQuestionResponse generateUserQuestions(UserQuestionRequest request) {
        var userGroups = new AclPayload(usersService.getUserGroups(request.userEmail()));

        var result = processUserQuery(
                new RagRetrievalRequest(request.queryId(), request.query(), userGroups),
                request.channel()
        );

        List<Citation> enrichedCitations;
        if (result.citations() == null || result.citations().isEmpty()) {
            enrichedCitations = List.of();
        } else if (result.status() == GenerationStatus.ANSWER_GENERATED) {
            enrichedCitations = documentMetadataService.enrichCitations(result.citations());
        } else {
            enrichedCitations = enrichCitationsSafe(request.queryId(), result.citations());
        }

        if (isEscalatableStatus(result.status())) {
            saveEscalationDraftSafe(request.queryId(), request.userEmail(), request.query(), enrichedCitations);
        }

        if (result.status() != GenerationStatus.ANSWER_GENERATED) {
            return new UserQuestionResponse(
                    request.queryId(),
                    QuestionStatus.ESCALATED_TO_EXPERT,
                    ESCALATED_REPLY_TEXT,
                    List.of()
            );
        }

        return new UserQuestionResponse(
                request.queryId(),
                QuestionStatus.ANSWERED,
                result.answer(),
                enrichedCitations
        );
    }

    RagRetrievalResponse processUserQuery(RagRetrievalRequest request, ChatChannel channel) {
        if (request.queryId() != null) {
            var existing = diagnosticsService.findByQueryId(request.queryId());
            if (existing.isPresent()) {
                var diag = existing.get();
                if (diag.getOutcome() != GenerationOutcome.FAILURE) {
                    logger.warn("QueryId {} has already been processed with outcome {}. Rejecting duplicate query.",
                            request.queryId(), diag.getOutcome());
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

        long startNanos = System.nanoTime();
        try {
            RagRetrievalResponse response = ragHttpClient.generateAnswer(request);
            int latencyMs = elapsedMillis(startNanos);
            recordOutcomeSafe(request.queryId(), channel, response, latencyMs);
            return response;
        } catch (RagGenerationException e) {
            logger.error("RAG pipeline failed for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), channel, e.getErrorCategory(), elapsedMillis(startNanos), null);
            throw e;
        } catch (ResourceAccessException e) {
            ErrorCategory category = isTimeout(e) ? ErrorCategory.RETRIEVAL_TIMEOUT : ErrorCategory.INTERNAL_CONNECTION_ERROR;
            logger.error("RAG request resource access error for query {}: {}", request.queryId(), e.getMessage());
            recordFailureSafe(request.queryId(), channel, category, elapsedMillis(startNanos), null);
            throw new RagGenerationException("RAG request failed: " + category, e, category);
        } catch (Exception e) {
            logger.error("Unexpected connection error for query {}", request.queryId(), e);
            recordFailureSafe(request.queryId(), channel, ErrorCategory.INTERNAL_CONNECTION_ERROR, elapsedMillis(startNanos), null);
            throw new RagGenerationException("Unexpected connection error", e, ErrorCategory.INTERNAL_CONNECTION_ERROR);
        }
    }

    private boolean isEscalatableStatus(GenerationStatus status) {
        return status == GenerationStatus.ANSWER_GENERATED
                || status == GenerationStatus.LOW_CONFIDENCE
                || status == GenerationStatus.EMPTY_RETRIEVAL;
    }

    private List<Citation> enrichCitationsSafe(String queryId, List<RagCitation> citations) {
        try {
            return documentMetadataService.enrichCitations(citations);
        } catch (Exception ex) {
            logger.warn("Failed to enrich citations for escalation draft of query {}", queryId, ex);
            return List.of();
        }
    }

    private void saveEscalationDraftSafe(
            String queryId,
            String requesterEmail,
            String questionText,
            List<Citation> enrichedCitations
    ) {
        try {
            escalationService.saveDraft(queryId, requesterEmail, questionText, enrichedCitations);
        } catch (Exception ex) {
            logger.warn("Failed to save temporary escalation draft for query {}", queryId, ex);
        }
    }

    private int elapsedMillis(long startNanos) {
        return Math.max(0, (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
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

    private void recordOutcomeSafe(String queryId, ChatChannel channel, RagRetrievalResponse response, int latencyMs) {
        try {
            GenerationStatus status = response.status();
            if (status == GenerationStatus.ANSWER_GENERATED) {
                diagnosticsService.recordAnswered(
                        queryId, channel, response.confidenceScore(), latencyMs, response.promptVersionId()
                );
            } else if (status == GenerationStatus.LOW_CONFIDENCE) {
                diagnosticsService.recordSafeNoAnswer(
                        queryId, channel, QuestionOutcomeReasonCode.LOW_CONFIDENCE,
                        response.confidenceScore(), latencyMs, response.promptVersionId()
                );
            } else if (status == GenerationStatus.EMPTY_RETRIEVAL) {
                diagnosticsService.recordSafeNoAnswer(
                        queryId, channel, QuestionOutcomeReasonCode.EMPTY_RETRIEVAL,
                        response.confidenceScore(), latencyMs, response.promptVersionId()
                );
            } else {
                diagnosticsService.recordFailure(
                        queryId, channel, ErrorCategory.INTERNAL_ERROR, latencyMs, response.promptVersionId()
                );
            }
        } catch (Exception ex) {
            logger.warn("Failed to record outcome diagnostics for query {}", queryId, ex);
        }
    }

    private void recordFailureSafe(String queryId, ChatChannel channel, ErrorCategory category, int latencyMs, UUID promptVersionId) {
        try {
            diagnosticsService.recordFailure(queryId, channel, category, latencyMs, promptVersionId);
        } catch (Exception ex) {
            logger.warn("Failed to record failure diagnostics for query {}", queryId, ex);
        }
    }
}