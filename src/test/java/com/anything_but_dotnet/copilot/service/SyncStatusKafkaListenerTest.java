package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.KafkaSyncStatusEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SyncStatusKafkaListenerTest {

    private DocumentSyncRunRepository repository;
    private SyncStatusKafkaListener listener;
    private final Instant fixedTime = Instant.parse("2026-10-31T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedTime, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        repository = mock(DocumentSyncRunRepository.class);
        listener = new SyncStatusKafkaListener(repository, fixedClock);
    }

    @Test
    void shouldCreateNewRunIfNotFound() {
        UUID syncId = UUID.randomUUID();
        KafkaSyncStatusEvent event = new KafkaSyncStatusEvent(
                syncId.toString(), "doc-123", SyncStatus.FAILED_PARSE, "Error", fixedTime.getEpochSecond()
        );

        when(repository.findById(syncId)).thenReturn(Optional.empty());

        listener.handleSyncStatusEvent(event);

        ArgumentCaptor<DocumentSyncRun> captor = ArgumentCaptor.forClass(DocumentSyncRun.class);
        verify(repository).save(captor.capture());

        DocumentSyncRun saved = captor.getValue();
        assertEquals(syncId, saved.getId());
        assertEquals("doc-123", saved.getDocumentId());
        assertEquals(SyncStatus.FAILED_PARSE, saved.getStatus());
        assertEquals(fixedTime, saved.getFinishedAt());
    }
}