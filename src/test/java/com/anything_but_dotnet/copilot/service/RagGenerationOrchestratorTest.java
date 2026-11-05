package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.*;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RagGenerationOrchestratorTest {

    private RagHttpClient httpClient;
    private GenerationDiagnosticsService diagnosticsService;
    private DocumentMetadataService documentMetadataService;
    private UsersService usersService;
    private RagGenerationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        httpClient = mock(RagHttpClient.class);
        diagnosticsService = mock(GenerationDiagnosticsService.class);
        documentMetadataService = mock(DocumentMetadataService.class);
        usersService = mock(UsersService.class);
        orchestrator = new RagGenerationOrchestrator(httpClient, diagnosticsService, documentMetadataService, usersService);
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
    void shouldRecordFailureAndRethrowOnException() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-2", "test", null);
        when(httpClient.generateAnswer(request)).thenThrow(
                new RagGenerationException("Error", ErrorCategory.LLM_PROVIDER_ERROR)
        );

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request)
        );

        assertEquals(ErrorCategory.LLM_PROVIDER_ERROR, thrown.getErrorCategory());
        verify(diagnosticsService).recordFailure("q-2", ErrorCategory.LLM_PROVIDER_ERROR);
    }

    @Test
    void shouldWrapUnexpectedExceptionInRagGenerationException() {
        RagRetrievalRequest request = new RagRetrievalRequest("q-3", "test", null);
        when(httpClient.generateAnswer(request)).thenThrow(new RuntimeException("Connection refused"));

        RagGenerationException thrown = assertThrows(
                RagGenerationException.class,
                () -> orchestrator.processUserQuery(request)
        );

        assertEquals(ErrorCategory.INTERNAL_CONNECTION_ERROR, thrown.getErrorCategory());
        verify(diagnosticsService).recordFailure("q-3", ErrorCategory.INTERNAL_CONNECTION_ERROR);
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
    }

    @Test
    void shouldEscalateToExpertWithoutEnrichingCitationsWhenEmptyRetrieval() {
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
                List.of()
        ));

        UserQuestionResponse response = orchestrator.generateUserQuestions(userRequest);

        assertEquals("req-123", response.queryId());
        assertEquals(QuestionStatus.ESCALATED_TO_EXPERT, response.status());
        assertTrue(response.citations().isEmpty());
        verifyNoInteractions(documentMetadataService);
    }

    @Test
    void shouldEscalateToExpertWithoutEnrichingCitationsWhenLowConfidence() {
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
                List.of(new RagCitation("doc-789", "987e6543-e21b-12d3-a456-426614174999", 0.42))
        ));

        UserQuestionResponse response = orchestrator.generateUserQuestions(userRequest);

        assertEquals("req-123", response.queryId());
        assertEquals(QuestionStatus.ESCALATED_TO_EXPERT, response.status());
        assertTrue(response.citations().isEmpty());
        verifyNoInteractions(documentMetadataService);
    }
}
