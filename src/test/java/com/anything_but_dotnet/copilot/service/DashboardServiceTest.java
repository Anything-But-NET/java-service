package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.GenerationLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.DocumentMetadataItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UserAclItem;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.repository.DocumentMetadataRepository;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import com.anything_but_dotnet.copilot.repository.UsersRepository;
import com.anything_but_dotnet.copilot.repository.EscalationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DocumentSyncRunRepository syncRunRepository;
    @Mock
    private GenerationDiagnosticsRepository diagnosticsRepository;
    @Mock
    private GenerationDiagnosticsService diagnosticsService;
    @Mock
    private EscalationRepository escalationRepository;
    @Mock
    private DocumentMetadataRepository documentMetadataRepository;
    @Mock
    private UsersRepository usersRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                syncRunRepository,
                diagnosticsRepository,
                diagnosticsService,
                escalationRepository,
                documentMetadataRepository,
                usersRepository
        );
    }

    @Test
    void getIngestionLogsRejectsInvalidDateRange() {
        Instant from = Instant.parse("2026-11-13T12:00:00Z");
        Instant to = Instant.parse("2026-11-13T11:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getIngestionLogs(0, 20, from, to, null, null)
        );
    }

    @Test
    void getGenerationLogsRejectsInvalidDateRange() {
        Instant from = Instant.parse("2026-11-13T12:00:00Z");
        Instant to = Instant.parse("2026-11-13T11:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getGenerationLogs(0, 20, from, to, null, null)
        );
    }

    @Test
    void delegatesAnalyticsQueriesToDiagnosticsService() {
        Instant from = Instant.parse("2026-11-18T00:00:00Z");
        Instant to = Instant.parse("2026-11-21T00:00:00Z");
        UUID promptId = UUID.randomUUID();
        QuestionOutcomesAnalyticsResponse expected = QuestionOutcomesAnalyticsResponse.empty(from, to);

        when(diagnosticsService.getAnalytics(from, to, ChatChannel.SLACK, null)).thenReturn(expected);
        when(diagnosticsService.getAnalytics(from, to, ChatChannel.TEAMS, promptId)).thenReturn(expected);

        assertEquals(expected, service.getQuestionOutcomeAnalytics(from, to, ChatChannel.SLACK));
        assertEquals(expected, service.getPromptOutcomeAnalytics(promptId, from, to, ChatChannel.TEAMS));

        verify(diagnosticsService).getAnalytics(from, to, ChatChannel.SLACK, null);
        verify(diagnosticsService).getAnalytics(from, to, ChatChannel.TEAMS, promptId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsMappedPagesForLogsDocumentsAndUsers() {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");

        DocumentSyncRun run = new DocumentSyncRun(UUID.randomUUID(), "doc-1", now);
        run.markCompleted(now.plusSeconds(5));
        when(syncRunRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run)));

        PageResponse<IngestionLogItem> ingestion = service.getIngestionLogs(
                0, 20, now.minusSeconds(60), now.plusSeconds(60), SyncStatus.COMPLETED, "doc-1"
        );
        assertEquals(1, ingestion.items().size());
        assertEquals(SyncStatus.COMPLETED, ingestion.items().getFirst().status());

        GenerationDiagnostics diag = GenerationDiagnostics.failure(
                UUID.randomUUID(), "req-1", ChatChannel.SLACK, ErrorCategory.LLM_PROVIDER_ERROR, 1500, null, now
        );
        when(diagnosticsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(diag)));

        PageResponse<GenerationLogItem> generation = service.getGenerationLogs(
                0, 20, null, null, GenerationOutcome.FAILURE, ErrorCategory.LLM_PROVIDER_ERROR
        );
        assertEquals(1, generation.items().size());
        assertEquals(ErrorCategory.LLM_PROVIDER_ERROR, generation.items().getFirst().errorCategory());
        assertEquals(ChatChannel.SLACK, generation.items().getFirst().channel());

        DocumentMetadata doc = DocumentMetadata.create("doc-1");
        doc.applyUpsert("sharepoint", "Title", "https://example.com/1", now, now);
        when(documentMetadataRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doc)));

        PageResponse<DocumentMetadataItem> docs = service.getDocuments(0, 20);
        assertEquals("doc-1", docs.items().getFirst().documentId());

        User user = new User("oleh@company.com");
        user.applyUpsert(List.of("devs"), now, now);
        when(usersRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        PageResponse<UserAclItem> users = service.getUsers(0, 20);
        assertEquals("oleh@company.com", users.items().getFirst().email());
    }

    @Test
    @SuppressWarnings("unchecked")
    void enrichesGenerationLogsWithLinkedEscalationStatus() {
        Instant now = Instant.parse("2026-11-23T10:15:30Z");
        GenerationDiagnostics diag = GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), "req-esc", ChatChannel.SLACK, null, 0.25, 1100, null, now
        );
        diag.markEscalated(now);
        UUID escalationId = UUID.randomUUID();
        Escalation escalation = Escalation.createPending(escalationId, "req-esc", now);

        when(diagnosticsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(diag)));
        when(escalationRepository.findByQueryIdIn(List.of("req-esc")))
                .thenReturn(List.of(escalation));

        PageResponse<GenerationLogItem> logs = service.getGenerationLogs(
                0, 20, null, null, GenerationOutcome.ESCALATED, null
        );

        assertEquals(1, logs.items().size());
        assertEquals(escalationId, logs.items().getFirst().escalationId());
        assertEquals(EscalationStatus.PENDING, logs.items().getFirst().resolutionStatus());
    }
}
