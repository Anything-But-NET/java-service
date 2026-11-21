package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationDiagnosticsServiceTest {

    private GenerationDiagnosticsRepository repository;
    private GenerationDiagnosticsService service;

    private final Instant fixedInstant = Instant.parse("2026-11-09T12:00:00Z");
    private final UUID promptVersionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        repository = mock(GenerationDiagnosticsRepository.class);
        Clock clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new GenerationDiagnosticsService(repository, clock, objectMapper);
    }

    @Test
    void shouldRecordAnsweredWhenQueryIdIsNew() {
        when(repository.existsByQueryId("q-answered")).thenReturn(false);

        boolean recorded = service.recordAnswered("q-answered", ChatChannel.SLACK, 0.95, 1150, promptVersionId);

        assertTrue(recorded);
        ArgumentCaptor<GenerationDiagnostics> captor = ArgumentCaptor.forClass(GenerationDiagnostics.class);
        verify(repository).save(captor.capture());

        GenerationDiagnostics saved = captor.getValue();
        assertEquals("q-answered", saved.getQueryId());
        assertEquals(ChatChannel.SLACK, saved.getChannel());
        assertEquals(GenerationOutcome.ANSWERED, saved.getOutcome());
        assertNull(saved.getReasonCode());
        assertNull(saved.getErrorCategory());
        assertEquals(0.95, saved.getConfidenceScore());
        assertEquals(1150, saved.getLatencyMs());
        assertEquals(promptVersionId, saved.getPromptVersionId());
        assertEquals(fixedInstant, saved.getCreatedAt());
        assertEquals(fixedInstant, saved.getUpdatedAt());
    }

    @Test
    void shouldNotRecordAnsweredWhenQueryIdAlreadyExists() {
        when(repository.existsByQueryId("q-dup")).thenReturn(true);

        boolean recorded = service.recordAnswered("q-dup", ChatChannel.SLACK, 0.95, 900, null);

        assertFalse(recorded);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRecordSafeNoAnswerWithReasonCode() {
        when(repository.existsByQueryId("q-no-answer")).thenReturn(false);

        boolean recorded = service.recordSafeNoAnswer(
                "q-no-answer", ChatChannel.TEAMS, QuestionOutcomeReasonCode.LOW_CONFIDENCE, 0.31, 1420, promptVersionId
        );

        assertTrue(recorded);
        ArgumentCaptor<GenerationDiagnostics> captor = ArgumentCaptor.forClass(GenerationDiagnostics.class);
        verify(repository).save(captor.capture());

        GenerationDiagnostics saved = captor.getValue();
        assertEquals("q-no-answer", saved.getQueryId());
        assertEquals(ChatChannel.TEAMS, saved.getChannel());
        assertEquals(GenerationOutcome.SAFE_NO_ANSWER, saved.getOutcome());
        assertEquals(QuestionOutcomeReasonCode.LOW_CONFIDENCE, saved.getReasonCode());
        assertNull(saved.getErrorCategory());
        assertEquals(0.31, saved.getConfidenceScore());
        assertEquals(1420, saved.getLatencyMs());
        assertEquals(promptVersionId, saved.getPromptVersionId());
    }

    @Test
    void shouldNotRecordDuplicateSafeNoAnswer() {
        when(repository.existsByQueryId("q-no-answer-dup")).thenReturn(true);

        boolean recorded = service.recordSafeNoAnswer(
                "q-no-answer-dup", ChatChannel.SLACK, QuestionOutcomeReasonCode.EMPTY_RETRIEVAL, 0.0, 500, null
        );

        assertFalse(recorded);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldTransitionSafeNoAnswerToEscalatedPreservingReasonCode() {
        Instant initialTime = fixedInstant.minusSeconds(30);
        GenerationDiagnostics existing = GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), "q-esc-trans", ChatChannel.SLACK,
                QuestionOutcomeReasonCode.EMPTY_RETRIEVAL, 0.0, 1000, promptVersionId, initialTime
        );
        when(repository.findFirstByQueryId("q-esc-trans")).thenReturn(Optional.of(existing));

        boolean escalated = service.markEscalated("q-esc-trans");

        assertTrue(escalated);
        verify(repository).save(existing);
        assertEquals(GenerationOutcome.ESCALATED, existing.getOutcome());
        assertEquals(QuestionOutcomeReasonCode.EMPTY_RETRIEVAL, existing.getReasonCode());
        assertEquals(initialTime, existing.getCreatedAt());
        assertEquals(fixedInstant, existing.getUpdatedAt());
    }

    @Test
    void shouldTransitionAnsweredToEscalatedAndIgnoreDuplicateOrUnknownEscalation() {
        GenerationDiagnostics existing = GenerationDiagnostics.answered(
                UUID.randomUUID(), "q-esc-ans", ChatChannel.SLACK, 0.85, 800, promptVersionId, fixedInstant.minusSeconds(30)
        );
        when(repository.findFirstByQueryId("q-esc-ans")).thenReturn(Optional.of(existing));

        assertTrue(service.markEscalated("q-esc-ans"));
        assertEquals(GenerationOutcome.ESCALATED, existing.getOutcome());
        assertNull(existing.getReasonCode());

        assertFalse(service.markEscalated("q-esc-ans"));
        verify(repository, times(1)).save(existing);

        when(repository.findFirstByQueryId("q-missing")).thenReturn(Optional.empty());
        assertFalse(service.markEscalated("q-missing"));
        assertFalse(service.markEscalated(null));
        assertFalse(service.markEscalated("  "));
    }

    @Test
    void shouldRecordFailureWhenQueryIdIsNew() {
        when(repository.existsByQueryId("q-fail")).thenReturn(false);

        boolean recorded = service.recordFailure("q-fail", ChatChannel.SLACK, ErrorCategory.RETRIEVAL_TIMEOUT, 5000, null);

        assertTrue(recorded);
        ArgumentCaptor<GenerationDiagnostics> captor = ArgumentCaptor.forClass(GenerationDiagnostics.class);
        verify(repository).save(captor.capture());

        GenerationDiagnostics saved = captor.getValue();
        assertEquals("q-fail", saved.getQueryId());
        assertEquals(ChatChannel.SLACK, saved.getChannel());
        assertEquals(GenerationOutcome.FAILURE, saved.getOutcome());
        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, saved.getErrorCategory());
        assertEquals(5000, saved.getLatencyMs());
        assertNull(saved.getConfidenceScore());
        assertNull(saved.getReasonCode());
    }

    @Test
    void shouldPreventDuplicateFailureWhenQueryIdAlreadyExists() {
        when(repository.existsByQueryId("q-retry")).thenReturn(true);

        boolean recorded = service.recordFailure("q-retry", ChatChannel.SLACK, ErrorCategory.RETRIEVAL_TIMEOUT, 5000, null);

        assertFalse(recorded);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCheckIfAlreadyProcessed() {
        when(repository.existsByQueryId("q-exists")).thenReturn(true);
        when(repository.existsByQueryId("q-not-exists")).thenReturn(false);

        assertTrue(service.isAlreadyProcessed("q-exists"));
        assertFalse(service.isAlreadyProcessed("q-not-exists"));
        assertFalse(service.isAlreadyProcessed(null));
        assertFalse(service.isAlreadyProcessed("   "));
    }

    @Test
    void shouldFindByQueryId() {
        GenerationDiagnostics mockDiagnostics = GenerationDiagnostics.failure(
                null, "q-123", ChatChannel.SLACK, ErrorCategory.RETRIEVAL_TIMEOUT, 2000, null, fixedInstant
        );
        when(repository.findFirstByQueryId("q-123")).thenReturn(Optional.of(mockDiagnostics));

        Optional<GenerationDiagnostics> result = service.findByQueryId("q-123");

        assertTrue(result.isPresent());
        assertEquals("q-123", result.get().getQueryId());
        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, result.get().getErrorCategory());
    }

    @Test
    void shouldDeserializeAnalyticsJsonAndAttachTimeRange() {
        Instant from = Instant.parse("2026-11-18T00:00:00Z");
        Instant to = Instant.parse("2026-11-21T00:00:00Z");
        String sqlJson = """
                {
                  "total_questions": 100,
                  "outcome_counts": [
                    {"outcome": "ANSWERED", "count": 72},
                    {"outcome": "SAFE_NO_ANSWER", "count": 15},
                    {"outcome": "ESCALATED", "count": 10},
                    {"outcome": "FAILURE", "count": 3}
                  ],
                  "avg_confidence_score": 0.78,
                  "confidence_buckets": {"high": 65, "medium": 20, "low": 12},
                  "avg_latency_ms": 1420.5,
                  "p95_latency_ms": 3180.0,
                  "reason_code_counts": [
                    {"reason_code": "LOW_CONFIDENCE", "count": 12},
                    {"reason_code": "EMPTY_RETRIEVAL", "count": 8}
                  ],
                  "error_category_counts": [
                    {"error_category": "RETRIEVAL_TIMEOUT", "count": 2}
                  ],
                  "channel_counts": [
                    {"channel": "SLACK", "count": 92},
                    {"channel": "TEAMS", "count": 8}
                  ],
                  "daily_trend": [
                    {
                      "date": "2026-11-18",
                      "total_questions": 30,
                      "outcome_counts": [{"outcome": "ANSWERED", "count": 22}]
                    }
                  ]
                }
                """;
        when(repository.getAnalyticsJson(from, to, "SLACK", promptVersionId)).thenReturn(sqlJson);

        QuestionOutcomesAnalyticsResponse result = service.getAnalytics(from, to, ChatChannel.SLACK, promptVersionId);

        assertEquals(from, result.from());
        assertEquals(to, result.to());
        assertEquals(100L, result.totalQuestions());
        assertEquals(4, result.outcomeCounts().size());
        assertEquals(0.78, result.avgConfidenceScore());
        assertEquals(65L, result.confidenceBuckets().high());
        assertEquals(1420.5, result.avgLatencyMs());
        assertEquals(3180.0, result.p95LatencyMs());
        assertEquals(2, result.reasonCodeCounts().size());
        assertEquals(1, result.errorCategoryCounts().size());
        assertEquals(ChatChannel.SLACK, result.channelCounts().getFirst().channel());
        assertEquals(LocalDate.of(2026, 11, 18), result.dailyTrend().getFirst().date());
    }

    @Test
    void shouldReturnEmptyAnalyticsWhenRepositoryReturnsNullAndRejectInvalidRange() {
        Instant from = Instant.parse("2026-11-21T00:00:00Z");
        Instant to = Instant.parse("2026-11-18T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.getAnalytics(from, to, null, null));

        when(repository.getAnalyticsJson(null, null, null, null)).thenReturn(null);
        QuestionOutcomesAnalyticsResponse empty = service.getAnalytics(null, null, null, null);
        assertEquals(0L, empty.totalQuestions());
        assertTrue(empty.outcomeCounts().isEmpty());
        assertEquals(0L, empty.confidenceBuckets().high());
    }
}
