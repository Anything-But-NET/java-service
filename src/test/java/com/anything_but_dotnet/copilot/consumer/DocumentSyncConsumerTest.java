package com.anything_but_dotnet.copilot.consumer;

import com.anything_but_dotnet.copilot.dto.SyncEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import com.anything_but_dotnet.copilot.service.DocumentMetadataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSyncConsumerTest {

    private static final String TOPIC = "rag.documents.ingestion.v1";
    private static final String SYNC_ID_STR = "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8";
    private static final UUID SYNC_ID = UUID.fromString(SYNC_ID_STR);

    @Mock
    private DocumentMetadataService metadataService;

    @Mock
    private DocumentSyncRunRepository syncRunRepository;

    private DocumentSyncConsumer consumer;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Використовуємо фіксований час для передбачуваності тестів
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("UTC"));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        consumer = new DocumentSyncConsumer(metadataService, objectMapper, syncRunRepository, fixedClock);
    }

    @Test
    void shouldParseAndForwardValidUpsertRecord() {
        String payload = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "create",
                  "source_system": "sharepoint",
                  "title": "IT Compensation Guidelines",
                  "url": "https://company.sharepoint.com/docs/it-comp",
                  "raw_content": "Text",
                  "last_updated": "2026-10-26T16:00:00Z",
                  "acl": {"allowed_groups": ["Engineering", "All Staff"]}
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 1L, "doc-456", payload);

        when(syncRunRepository.findById(SYNC_ID)).thenReturn(Optional.empty());

        consumer.consume(record);

        // Перевіряємо Крок А: Збереження метаданих
        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(metadataService).processEvent(captor.capture());
        SyncEvent.Upsert event = assertInstanceOf(SyncEvent.Upsert.class, captor.getValue());
        assertEquals("doc-456", event.documentId());
        assertEquals("create", event.action());

        // Перевіряємо Крок Б: Збереження IN_PROGRESS статусу
        ArgumentCaptor<DocumentSyncRun> runCaptor = ArgumentCaptor.forClass(DocumentSyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        DocumentSyncRun savedRun = runCaptor.getValue();
        assertEquals(SYNC_ID, savedRun.getId());
        assertEquals("doc-456", savedRun.getDocumentId());
    }

    @Test
    void shouldParseAndForwardValidDeleteRecord() {
        String payload = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "delete"
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 2L, "doc-456", payload);

        when(syncRunRepository.findById(SYNC_ID)).thenReturn(Optional.empty());

        consumer.consume(record);

        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(metadataService).processEvent(captor.capture());

        SyncEvent.Delete event = assertInstanceOf(SyncEvent.Delete.class, captor.getValue());
        assertEquals("doc-456", event.documentId());

        // Перевіряємо Крок Б для Delete
        verify(syncRunRepository).save(any(DocumentSyncRun.class));
    }

    @Test
    void shouldSkipNullValueRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 3L, "doc-456", null);

        consumer.consume(record);

        verify(metadataService, never()).processEvent(any());
        verifyNoInteractions(syncRunRepository);
    }

    @Test
    void shouldThrowExceptionWhenDocumentIdIsMissing() {
        String payload = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "action": "delete"
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 4L, null, payload);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(record)
        );

        assertEquals("Broken ingestion event", exception.getMessage());
        verify(metadataService, never()).processEvent(any());
        verifyNoInteractions(syncRunRepository);
    }

    @Test
    void shouldThrowExceptionWhenDocumentIdIsBlank() {
        String payload = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "   ",
                  "action": "delete"
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 5L, null, payload);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(record)
        );

        assertEquals("Broken ingestion event", exception.getMessage());
        verify(metadataService, never()).processEvent(any());
        verifyNoInteractions(syncRunRepository);
    }

    @Test
    void shouldThrowExceptionWhenActionIsUnknown() {
        String invalidActionPayload = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "unknown_action"
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 6L, "doc-456", invalidActionPayload);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(record)
        );

        assertEquals("Incorrect ingestion payload", exception.getMessage());
        assertInstanceOf(JsonProcessingException.class, exception.getCause());
        verify(metadataService, never()).processEvent(any());
        verifyNoInteractions(syncRunRepository);
    }

    @Test
    void shouldThrowExceptionWhenJsonIsMalformed() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 7L, "doc-456", "{broken-json");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(record)
        );

        assertEquals("Incorrect ingestion payload", exception.getMessage());
        assertInstanceOf(JsonProcessingException.class, exception.getCause());
        verify(metadataService, never()).processEvent(any());
        verifyNoInteractions(syncRunRepository);
    }
}