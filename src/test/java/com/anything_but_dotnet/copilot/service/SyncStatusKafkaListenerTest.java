package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.KafkaSyncStatusEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.SyncStatus;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncStatusKafkaListenerTest {

    @Mock
    private DocumentSyncRunRepository repository;

    @InjectMocks
    private SyncStatusKafkaListener listener;

    private UUID syncId;
    private String documentId;

    @BeforeEach
    void setUp() {
        syncId = UUID.randomUUID();
        documentId = "doc-123";
    }

    @Test
    void shouldCreateNewRunWhenReceivingEventForFirstTime() {
        // Given: Event arrives for a sync_id that doesn't exist in DB
        KafkaSyncStatusEvent event = new KafkaSyncStatusEvent(
                syncId, documentId, SyncStatus.FAILED_FETCH, "Timeout", 1730000000L
        );
        when(repository.findById(syncId)).thenReturn(Optional.empty());

        // When
        listener.handleSyncStatusEvent(event);

        // Then: verify repository.save was called and capture the saved entity
        ArgumentCaptor<DocumentSyncRun> captor = ArgumentCaptor.forClass(DocumentSyncRun.class);
        verify(repository, times(1)).save(captor.capture());

        DocumentSyncRun savedRun = captor.getValue();
        assertEquals(syncId, savedRun.getId());
        assertEquals(documentId, savedRun.getDocumentId());
        assertEquals(SyncStatus.FAILED_FETCH, savedRun.getStatus());
        assertEquals("Timeout", savedRun.getErrorMessage());
        assertNotNull(savedRun.getStartedAt());
        assertNotNull(savedRun.getFinishedAt()); // Should be set because it's a terminal status
    }

    @Test
    void shouldUpdateExistingRunWhenReceivingSubsequentEvent() {
        // Given: Event arrives for a sync_id that ALREADY exists in DB
        KafkaSyncStatusEvent event = new KafkaSyncStatusEvent(
                syncId, documentId, SyncStatus.COMPLETED, null, 1730000000L
        );

        DocumentSyncRun existingRun = new DocumentSyncRun(syncId, documentId, SyncStatus.IN_PROGRESS, null);
        when(repository.findById(syncId)).thenReturn(Optional.of(existingRun));

        // When
        listener.handleSyncStatusEvent(event);

        // Then
        ArgumentCaptor<DocumentSyncRun> captor = ArgumentCaptor.forClass(DocumentSyncRun.class);
        verify(repository, times(1)).save(captor.capture());

        DocumentSyncRun updatedRun = captor.getValue();
        assertEquals(SyncStatus.COMPLETED, updatedRun.getStatus());
        assertNotNull(updatedRun.getFinishedAt());
    }
}