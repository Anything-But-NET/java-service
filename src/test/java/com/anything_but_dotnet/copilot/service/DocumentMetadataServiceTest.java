package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.SyncEvent;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.repository.DocumentMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentMetadataServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-26T18:00:00Z");

    @Mock
    private DocumentMetadataRepository repository;

    private DocumentMetadataService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new DocumentMetadataService(repository, fixedClock);
    }

    @Test
    void shouldCreateNewDocumentOnUpsertEvent() {
        Instant eventUpdated = Instant.parse("2026-10-26T16:00:00Z");
        SyncEvent.Upsert event = new SyncEvent.Upsert(
                "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                "doc-456",
                "create",
                "sharepoint",
                "IT Compensation Guidelines",
                "https://company.sharepoint.com/docs/it-comp",
                "Full text",
                eventUpdated,
                new SyncEvent.AclPayload(List.of("devs", "all-employees"))
        );

        when(repository.findById("doc-456")).thenReturn(Optional.empty());

        service.processEvent(event);

        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(repository).save(captor.capture());

        DocumentMetadata saved = captor.getValue();
        assertEquals("doc-456", saved.getDocumentId());
        assertEquals("sharepoint", saved.getSourceSystem());
        assertEquals("IT Compensation Guidelines", saved.getTitle());
        assertEquals("https://company.sharepoint.com/docs/it-comp", saved.getUrl());
        assertEquals(eventUpdated, saved.getLastUpdated());
        assertEquals(List.of("devs", "all-employees"), saved.getAllowedGroups());
        assertEquals(FIXED_NOW, saved.getLastSyncAt());
    }

    @Test
    void shouldSkipStaleUpsertEvent() {
        DocumentMetadata existing = DocumentMetadata.create("doc-456");
        existing.applyUpsert(
                "sharepoint",
                "Newer Title",
                "https://company.sharepoint.com/docs/it-comp",
                Instant.parse("2026-10-26T17:00:00Z"),
                List.of("devs"),
                Instant.parse("2026-10-26T17:01:00Z")
        );

        when(repository.findById("doc-456")).thenReturn(Optional.of(existing));

        SyncEvent.Upsert staleEvent = new SyncEvent.Upsert(
                "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                "doc-456",
                "update",
                "sharepoint",
                "Older Title",
                "https://company.sharepoint.com/docs/it-comp",
                "Old text",
                Instant.parse("2026-10-26T16:00:00Z"),
                new SyncEvent.AclPayload(List.of("all-employees"))
        );

        service.processEvent(staleEvent);

        verify(repository, never()).save(any());
        assertEquals("Newer Title", existing.getTitle());
    }

    @Test
    void shouldUpdateOnlyAllowedGroupsOnUpdateAclEvent() {
        DocumentMetadata existing = DocumentMetadata.create("doc-456");
        Instant originalUpdated = Instant.parse("2026-10-26T15:00:00Z");
        existing.applyUpsert(
                "sharepoint",
                "Existing Title",
                "https://company.sharepoint.com/docs/it-comp",
                originalUpdated,
                List.of("devs"),
                Instant.parse("2026-10-26T15:05:00Z")
        );

        when(repository.findById("doc-456")).thenReturn(Optional.of(existing));

        SyncEvent.UpdateAcl aclEvent = new SyncEvent.UpdateAcl(
                "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                "doc-456",
                new SyncEvent.AclPayload(List.of("Engineering - Backend"))
        );

        service.processEvent(aclEvent);

        verify(repository).save(existing);
        assertEquals("Existing Title", existing.getTitle());
        assertEquals(originalUpdated, existing.getLastUpdated());
        assertEquals(List.of("Engineering - Backend"), existing.getAllowedGroups());
        assertEquals(FIXED_NOW, existing.getLastSyncAt());
    }

    @Test
    void shouldDeleteDocumentOnDeleteEvent() {
        SyncEvent.Delete deleteEvent = new SyncEvent.Delete(
                "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                "doc-456"
        );

        service.processEvent(deleteEvent);

        verify(repository).deleteById("doc-456");
    }
}
