package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.EscalationResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationDetail;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationItem;
import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.repository.DashboardOperatorRepository;
import com.anything_but_dotnet.copilot.repository.EscalationRepository;
import com.anything_but_dotnet.copilot.repository.EscalationStatusHistoryRepository;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EscalationServiceTest {

    private EscalationRepository escalationRepository;
    private EscalationStatusHistoryRepository historyRepository;
    private GenerationDiagnosticsRepository diagnosticsRepository;
    private GenerationDiagnosticsService diagnosticsService;
    private DashboardOperatorRepository operatorRepository;
    private EscalationService service;

    private final Instant fixedInstant = Instant.parse("2026-11-23T10:15:30Z");

    @BeforeEach
    void setUp() {
        escalationRepository = mock(EscalationRepository.class);
        historyRepository = mock(EscalationStatusHistoryRepository.class);
        diagnosticsRepository = mock(GenerationDiagnosticsRepository.class);
        diagnosticsService = mock(GenerationDiagnosticsService.class);
        operatorRepository = mock(DashboardOperatorRepository.class);
        Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        service = new EscalationService(
                escalationRepository,
                historyRepository,
                diagnosticsRepository,
                diagnosticsService,
                operatorRepository,
                clock
        );
    }

    @Test
    void createOrGetCreatesPendingEscalationAndRecordsInitialHistory() {
        GenerationDiagnostics diag = GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), "req-123", ChatChannel.SLACK,
                QuestionOutcomeReasonCode.LOW_CONFIDENCE, 0.31, 1200, null, fixedInstant.minusSeconds(10)
        );
        when(escalationRepository.findByQueryId("req-123")).thenReturn(Optional.empty());
        when(diagnosticsService.findByQueryId("req-123")).thenReturn(Optional.of(diag));

        EscalationResponse response = service.createOrGet("req-123");

        assertNotNull(response.escalationId());
        assertEquals("req-123", response.queryId());
        assertEquals(EscalationStatus.PENDING, response.status());
        assertEquals(fixedInstant, response.createdAt());
        assertEquals(fixedInstant, response.updatedAt());

        verify(diagnosticsService).markEscalated("req-123");

        ArgumentCaptor<Escalation> escCaptor = ArgumentCaptor.forClass(Escalation.class);
        verify(escalationRepository).save(escCaptor.capture());
        assertEquals(response.escalationId(), escCaptor.getValue().getId());
        assertEquals(EscalationStatus.PENDING, escCaptor.getValue().getStatus());

        ArgumentCaptor<EscalationStatusHistory> histCaptor = ArgumentCaptor.forClass(EscalationStatusHistory.class);
        verify(historyRepository).save(histCaptor.capture());
        EscalationStatusHistory savedHistory = histCaptor.getValue();
        assertEquals(response.escalationId(), savedHistory.getEscalationId());
        assertNull(savedHistory.getFromStatus());
        assertEquals(EscalationStatus.PENDING, savedHistory.getToStatus());
        assertNull(savedHistory.getChangedByOperatorId());
        assertEquals(fixedInstant, savedHistory.getChangedAt());
    }

    @Test
    void createOrGetIsIdempotentAndReturnsExistingEscalationWithoutDuplicates() {
        UUID existingId = UUID.randomUUID();
        Escalation existing = Escalation.createPending(existingId, "req-dup", fixedInstant.minusSeconds(60));
        existing.transitionTo(EscalationStatus.IN_PROGRESS, fixedInstant.minusSeconds(30));

        when(escalationRepository.findByQueryId("req-dup")).thenReturn(Optional.of(existing));

        EscalationResponse response = service.createOrGet("req-dup");

        assertEquals(existingId, response.escalationId());
        assertEquals("req-dup", response.queryId());
        assertEquals(EscalationStatus.IN_PROGRESS, response.status());

        verify(diagnosticsService, never()).markEscalated(any());
        verify(escalationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void createOrGetRejectsBlankUnknownOrFailedQueryId() {
        assertThrows(IllegalArgumentException.class, () -> service.createOrGet("   "));

        when(escalationRepository.findByQueryId("req-missing")).thenReturn(Optional.empty());
        when(diagnosticsService.findByQueryId("req-missing")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createOrGet("req-missing"));

        GenerationDiagnostics failedDiag = GenerationDiagnostics.failure(
                UUID.randomUUID(), "req-failed", ChatChannel.SLACK,
                ErrorCategory.RETRIEVAL_TIMEOUT, 5000, null, fixedInstant.minusSeconds(10)
        );
        when(escalationRepository.findByQueryId("req-failed")).thenReturn(Optional.empty());
        when(diagnosticsService.findByQueryId("req-failed")).thenReturn(Optional.of(failedDiag));
        assertThrows(IllegalArgumentException.class, () -> service.createOrGet("req-failed"));

        verify(escalationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void updateStatusTransitionsLifecycleSetsResolvedAtAndLogsOperatorHistory() {
        UUID escalationId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Escalation escalation = Escalation.createPending(escalationId, "req-life", fixedInstant.minusSeconds(120));

        DashboardOperator operator = mock(DashboardOperator.class);
        when(operator.getId()).thenReturn(operatorId);

        when(escalationRepository.findById(escalationId)).thenReturn(Optional.of(escalation));
        when(operatorRepository.findByEmail("expert@company.com")).thenReturn(Optional.of(operator));
        when(diagnosticsService.findByQueryId("req-life")).thenReturn(Optional.empty());

        EscalationItem inProgress = service.updateStatus(escalationId, EscalationStatus.IN_PROGRESS, "expert@company.com");
        assertEquals(EscalationStatus.IN_PROGRESS, inProgress.status());
        assertNull(inProgress.resolvedAt());

        EscalationItem resolved = service.updateStatus(escalationId, EscalationStatus.RESOLVED, "expert@company.com");
        assertEquals(EscalationStatus.RESOLVED, resolved.status());
        assertEquals(fixedInstant, resolved.resolvedAt());

        EscalationItem closed = service.updateStatus(escalationId, EscalationStatus.CLOSED, "expert@company.com");
        assertEquals(EscalationStatus.CLOSED, closed.status());
        assertEquals(fixedInstant, closed.resolvedAt());

        ArgumentCaptor<EscalationStatusHistory> histCaptor = ArgumentCaptor.forClass(EscalationStatusHistory.class);
        verify(historyRepository, times(3)).save(histCaptor.capture());

        List<EscalationStatusHistory> entries = histCaptor.getAllValues();
        assertEquals(EscalationStatus.PENDING, entries.get(0).getFromStatus());
        assertEquals(EscalationStatus.IN_PROGRESS, entries.get(0).getToStatus());
        assertEquals(operatorId, entries.get(0).getChangedByOperatorId());

        assertEquals(EscalationStatus.IN_PROGRESS, entries.get(1).getFromStatus());
        assertEquals(EscalationStatus.RESOLVED, entries.get(1).getToStatus());

        assertEquals(EscalationStatus.RESOLVED, entries.get(2).getFromStatus());
        assertEquals(EscalationStatus.CLOSED, entries.get(2).getToStatus());
    }

    @Test
    void updateStatusIsIdempotentForSameStatusAndRejectsInvalidTransitions() {
        UUID escalationId = UUID.randomUUID();
        Escalation escalation = Escalation.createPending(escalationId, "req-state", fixedInstant.minusSeconds(60));
        when(escalationRepository.findById(escalationId)).thenReturn(Optional.of(escalation));
        when(diagnosticsService.findByQueryId("req-state")).thenReturn(Optional.empty());

        // Same status -> no-op
        EscalationItem same = service.updateStatus(escalationId, EscalationStatus.PENDING, "expert@company.com");
        assertEquals(EscalationStatus.PENDING, same.status());
        verify(escalationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());

        // PENDING -> CLOSED directly is not allowed
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(escalationId, EscalationStatus.CLOSED, "expert@company.com")
        );

        // Cancel -> terminal state, further transitions rejected
        service.updateStatus(escalationId, EscalationStatus.CANCELLED, "expert@company.com");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(escalationId, EscalationStatus.IN_PROGRESS, "expert@company.com")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEscalationsEnrichesWithDiagnosticsAndValidatesTimeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getEscalations(0, 20, fixedInstant, fixedInstant.minusSeconds(10), null, null)
        );

        UUID escalationId = UUID.randomUUID();
        Escalation escalation = Escalation.createPending(escalationId, "req-queue", fixedInstant);
        GenerationDiagnostics diag = GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), "req-queue", ChatChannel.TEAMS,
                QuestionOutcomeReasonCode.EMPTY_RETRIEVAL, 0.0, 800, null, fixedInstant
        );

        when(escalationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(escalation)));
        when(diagnosticsRepository.findByQueryIdIn(List.of("req-queue")))
                .thenReturn(List.of(diag));

        PageResponse<EscalationItem> page = service.getEscalations(0, 20, null, null, EscalationStatus.PENDING, "req-queue");

        assertEquals(1, page.items().size());
        EscalationItem item = page.items().getFirst();
        assertEquals(escalationId, item.id());
        assertEquals("req-queue", item.queryId());
        assertEquals(EscalationStatus.PENDING, item.status());
        assertEquals(ChatChannel.TEAMS, item.channel());
        assertEquals(QuestionOutcomeReasonCode.EMPTY_RETRIEVAL, item.reasonCode());
        assertEquals(0.0, item.confidenceScore());
    }

    @Test
    void getEscalationByIdReturnsDetailWithHistoryAndThrowsWhenMissing() {
        UUID escalationId = UUID.randomUUID();
        Escalation escalation = Escalation.createPending(escalationId, "req-det", fixedInstant);
        EscalationStatusHistory entry = new EscalationStatusHistory(
                UUID.randomUUID(), escalationId, null, EscalationStatus.PENDING, null, fixedInstant
        );

        when(escalationRepository.findById(escalationId)).thenReturn(Optional.of(escalation));
        when(diagnosticsService.findByQueryId("req-det")).thenReturn(Optional.empty());
        when(historyRepository.findByEscalationIdOrderByChangedAtAsc(escalationId)).thenReturn(List.of(entry));

        EscalationDetail detail = service.getEscalationById(escalationId);
        assertEquals(escalationId, detail.id());
        assertEquals(1, detail.statusHistory().size());
        assertEquals(EscalationStatus.PENDING, detail.statusHistory().getFirst().toStatus());

        UUID missingId = UUID.randomUUID();
        when(escalationRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getEscalationById(missingId));
    }
}