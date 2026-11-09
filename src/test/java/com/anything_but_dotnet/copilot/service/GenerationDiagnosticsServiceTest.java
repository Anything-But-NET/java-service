package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationDiagnosticsServiceTest {

    private GenerationDiagnosticsRepository repository;
    private Clock clock;
    private GenerationDiagnosticsService service;

    private final Instant fixedInstant = Instant.parse("2026-11-09T12:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(GenerationDiagnosticsRepository.class);
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        service = new GenerationDiagnosticsService(repository, clock);
    }

    @Test
    void shouldRecordSuccessWhenQueryIdIsNew() {
        when(repository.existsByQueryId("q-success")).thenReturn(false);

        boolean recorded = service.recordSuccess("q-success", 0.95);

        assertTrue(recorded);
        ArgumentCaptor<GenerationDiagnostics> captor = ArgumentCaptor.forClass(GenerationDiagnostics.class);
        verify(repository).save(captor.capture());

        GenerationDiagnostics saved = captor.getValue();
        assertEquals("q-success", saved.getQueryId());
        assertEquals(GenerationOutcome.SUCCESS, saved.getOutcome());
        assertEquals(0.95, saved.getConfidenceScore());
        assertEquals(fixedInstant, saved.getCreatedAt());
        assertNull(saved.getErrorCategory());
    }

    @Test
    void shouldNotRecordSuccessWhenQueryIdAlreadyExists() {
        when(repository.existsByQueryId("q-dup")).thenReturn(true);

        boolean recorded = service.recordSuccess("q-dup", 0.95);

        assertFalse(recorded);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRecordFailureWhenQueryIdIsNew() {
        when(repository.existsByQueryId("q-fail")).thenReturn(false);

        boolean recorded = service.recordFailure("q-fail", ErrorCategory.RETRIEVAL_TIMEOUT);

        assertTrue(recorded);
        ArgumentCaptor<GenerationDiagnostics> captor = ArgumentCaptor.forClass(GenerationDiagnostics.class);
        verify(repository).save(captor.capture());

        GenerationDiagnostics saved = captor.getValue();
        assertEquals("q-fail", saved.getQueryId());
        assertEquals(GenerationOutcome.FAILURE, saved.getOutcome());
        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, saved.getErrorCategory());
        assertEquals(fixedInstant, saved.getCreatedAt());
        assertNull(saved.getConfidenceScore());
    }

    @Test
    void shouldPreventDuplicateFailureOrEscalationWhenQueryIdAlreadyExists() {
        when(repository.existsByQueryId("q-retry")).thenReturn(true);

        boolean recorded = service.recordFailure("q-retry", ErrorCategory.RETRIEVAL_TIMEOUT);

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
                null, "q-123", ErrorCategory.RETRIEVAL_TIMEOUT, fixedInstant
        );
        when(repository.findFirstByQueryId("q-123")).thenReturn(Optional.of(mockDiagnostics));

        Optional<GenerationDiagnostics> result = service.findByQueryId("q-123");

        assertTrue(result.isPresent());
        assertEquals("q-123", result.get().getQueryId());
        assertEquals(ErrorCategory.RETRIEVAL_TIMEOUT, result.get().getErrorCategory());
    }
}
