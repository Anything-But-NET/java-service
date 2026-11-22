package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.*;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class RagGenerationOrchestratorTest {

    private RagHttpClient httpClient;
    private GenerationDiagnosticsService diagnosticsService;
    private DocumentMetadataService documentMetadataService;
    private UsersService usersService;
    private RagGenerationOrchestrator orchestrator;

    private final UUID promptVersionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        httpClient = mock(RagHttpClient.class);
        diagnosticsService = mock(GenerationDiagnosticsService.class);
        documentMetadataService = mock(DocumentMetadataService.class);
        usersService = mock(UsersService.class);
        orchestrator = new RagGenerationOrchestrator(httpClient, diagnosticsService, documentMetadataService, usersService);
    }

    @Test
    void shouldReturnResponseAndRecordAnsweredOnSuccess() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-1", "test", null);
        RagRetrievalResponse mockResponse = new RagRetrievalResponse(
                "q-1", GenerationStatus.ANSWER_GENERATED, "answer", 0.9, promptVersionId, List.of()
        );

        when(httpClient.generateAnswer(request)).thenReturn(mockResponse);

        RagRetrievalResponse response = orchestrator.processUserQuery(request, ChatChannel.TEAMS);

        assertEquals("q-1", response.queryId());
        assertEquals(GenerationStatus.ANSWER_GENERATED, response.status());
        verify(diagnosticsService).recordAnswered(eq("q-1"), eq(ChatChannel.TEAMS), eq(0.9), anyInt(), eq(promptVersionId));
    }

    @Test
    void shouldThrowExceptionAndRecordFailureOnRagException() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-2", "test", null);
        when(httpClient.generateAnswer(request)).thenThrow(
                new RagGenerationException("Error", ErrorCategory.LLM_PROVIDER_ERROR)
        );

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request, ChatChannel.SLACK)
        );

        assertEquals(ErrorCategory.LLM_PROVIDER_ERROR, thrown.getErrorCategory());
        verify(diagnosticsService).recordFailure(eq("q-2"), eq(ChatChannel.SLACK), eq(ErrorCategory.LLM_PROVIDER_ERROR), anyInt(), isNull());
    }

    @Test
    void shouldWrapUnexpectedExceptionInRagGenerationException() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-3", "test", null);
        when(httpClient.generateAnswer(request)).thenThrow(new RuntimeException("Connection refused"));

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request, ChatChannel.SLACK)
        );

        assertEquals(ErrorCategory.INTERNAL_CONNECTION_ERROR, thrown.getErrorCategory());
        verify(diagnosticsService).recordFailure(eq("q-3"), eq(ChatChannel.SLACK), eq(ErrorCategory.INTERNAL_CONNECTION_ERROR), anyInt(), isNull());
    }

    @Test
    void shouldHandleTimeoutAndRecordRetrievalTimeoutWhenResourceAccessExceptionIsTimeout() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-timeout", "test", null);
        SocketTimeoutException socketTimeout = new SocketTimeoutException("Read timed out");
        ResourceAccessException accessException =
                new ResourceAccessException("I/O error on POST request", socketTimeout);

        when(httpClient.generateAnswer(request)).thenThrow(accessException);

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request, ChatChannel.SLACK)
        );

        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, thrown.getErrorCategory());
        verify(diagnosticsService).recordFailure(eq("q-timeout"), eq(ChatChannel.SLACK), eq(ErrorCategory.RETRIEVAL_TIMEOUT), anyInt(), isNull());
    }

    @Test
    void shouldPreventDuplicateProcessingOnRetryWhenQueryAlreadyFailed() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-retry", "test", null);
        GenerationDiagnostics existing = GenerationDiagnostics.failure(
                null, "q-retry", ChatChannel.SLACK, ErrorCategory.RETRIEVAL_TIMEOUT, 3000, null, Instant.now()
        );
        when(diagnosticsService.findByQueryId("q-retry")).thenReturn(Optional.of(existing));

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request, ChatChannel.SLACK)
        );

        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, thrown.getErrorCategory());
        verify(httpClient, never()).generateAnswer(any());
        verify(diagnosticsService, never()).recordFailure(any(), any(), any(), any(), any());
        verify(diagnosticsService, never()).recordAnswered(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectDuplicateQueryWhenQueryAlreadyAnsweredOrSafeNoAnswer() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-answered-dup", "test", null);
        GenerationDiagnostics existing = GenerationDiagnostics.answered(
                null, "q-answered-dup", ChatChannel.SLACK, 0.95, 800, null, Instant.now()
        );
        when(diagnosticsService.findByQueryId("q-answered-dup")).thenReturn(Optional.of(existing));

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request, ChatChannel.SLACK)
        );

        assertEquals(ErrorCategory.VALIDATION_ERROR, thrown.getErrorCategory());
        verify(httpClient, never()).generateAnswer(any());
        verify(diagnosticsService, never()).recordFailure(any(), any(), any(), any(), any());
        verify(diagnosticsService, never()).recordAnswered(any(), any(), any(), any(), any());

        RagRetrievalRequest noAnswerDup = new RagRetrievalRequest("q-no-ans-dup", "test", null);
        GenerationDiagnostics existingNoAns = GenerationDiagnostics.safeNoAnswer(
                null, "q-no-ans-dup", ChatChannel.SLACK, QuestionOutcomeReasonCode.LOW_CONFIDENCE, 0.3, 900, null, Instant.now()
        );
        when(diagnosticsService.findByQueryId("q-no-ans-dup")).thenReturn(Optional.of(existingNoAns));

        RagGenerationException thrown2 = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(noAnswerDup, ChatChannel.SLACK)
        );
        assertEquals(ErrorCategory.VALIDATION_ERROR, thrown2.getErrorCategory());
    }

    @Test
    void shouldGenerateAnsweredResponseWithEnrichedCitations() {
        UserQuestionRequest userRequest = new UserQuestionRequest(
                "req-123",
                ChatChannel.TEAMS,
                "oleh.pona@company.com",
                "How to get internet compensation?"
        );

        List<RagCitation> ragCitations = List.of(
                new RagCitation("doc-456", "123e4567-e89b-12d3-a456-426614174000", 0.95)
        );
        List<Citation> enrichedCitations = List.of(
                new Citation(
                        "IT Compensation Guidelines",
                        "https://company.sharepoint.com/docs/it-comp",
                        "doc-456",
                        "123e4567-e89b-12d3-a456-426614174000",
                        0.95
                )
        );

        when(usersService.getUserGroups("oleh.pona@company.com")).thenReturn(List.of("devs", "all-employees"));
        when(httpClient.generateAnswer(any())).thenReturn(new RagRetrievalResponse(
                "req-123",
                GenerationStatus.ANSWER_GENERATED,
                "To get internet compensation, submit the reimbursement form on SharePoint.",
                0.89,
                promptVersionId,
                ragCitations
        ));
        when(documentMetadataService.enrichCitations(ragCitations)).thenReturn(enrichedCitations);

        UserQuestionResponse response = orchestrator.generateUserQuestions(userRequest);

        assertEquals("req-123", response.queryId());
        assertEquals(QuestionStatus.ANSWERED, response.status());
        assertEquals("To get internet compensation, submit the reimbursement form on SharePoint.", response.replyText());
        assertEquals(enrichedCitations, response.citations());
        verify(usersService).getUserGroups("oleh.pona@company.com");
        verify(documentMetadataService).enrichCitations(ragCitations);
        verify(diagnosticsService).recordAnswered(eq("req-123"), eq(ChatChannel.TEAMS), eq(0.89), anyInt(), eq(promptVersionId));
    }

    @Test
    void shouldRecordSafeNoAnswerWithEmptyRetrievalWithoutEnrichingCitations() {
        UserQuestionRequest userRequest = new UserQuestionRequest(
                "req-123",
                ChatChannel.SLACK,
                "oleh.pona@company.com",
                "How to get internet compensation?"
        );

        when(httpClient.generateAnswer(any())).thenReturn(new RagRetrievalResponse(
                "req-123",
                GenerationStatus.EMPTY_RETRIEVAL,
                null,
                0.0,
                promptVersionId,
                List.of()
        ));

        UserQuestionResponse response = orchestrator.generateUserQuestions(userRequest);

        assertEquals("req-123", response.queryId());
        assertEquals(QuestionStatus.ESCALATED_TO_EXPERT, response.status());
        assertTrue(response.citations().isEmpty());
        verifyNoInteractions(documentMetadataService);
        verify(diagnosticsService).recordSafeNoAnswer(
                eq("req-123"), eq(ChatChannel.SLACK), eq(QuestionOutcomeReasonCode.EMPTY_RETRIEVAL),
                eq(0.0), anyInt(), eq(promptVersionId)
        );
    }

    @Test
    void shouldRecordSafeNoAnswerWithLowConfidenceWithoutEnrichingCitations() {
        UserQuestionRequest userRequest = new UserQuestionRequest(
                "req-123",
                ChatChannel.TEAMS,
                "oleh.pona@company.com",
                "How to get internet compensation?"
        );

        when(httpClient.generateAnswer(any())).thenReturn(new RagRetrievalResponse(
                "req-123",
                GenerationStatus.LOW_CONFIDENCE,
                "Partial uncertain answer...",
                0.35,
                promptVersionId,
                List.of(new RagCitation("doc-789", "987e6543-e21b-12d3-a456-426614174999", 0.42))
        ));

        UserQuestionResponse response = orchestrator.generateUserQuestions(userRequest);

        assertEquals("req-123", response.queryId());
        assertEquals(QuestionStatus.ESCALATED_TO_EXPERT, response.status());
        assertTrue(response.citations().isEmpty());
        verifyNoInteractions(documentMetadataService);
        verify(diagnosticsService).recordSafeNoAnswer(
                eq("req-123"), eq(ChatChannel.TEAMS), eq(QuestionOutcomeReasonCode.LOW_CONFIDENCE),
                eq(0.35), anyInt(), eq(promptVersionId)
        );
    }
}
