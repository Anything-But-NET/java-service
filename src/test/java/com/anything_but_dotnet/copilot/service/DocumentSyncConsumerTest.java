package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.SyncEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentSyncConsumerTest {

    private static final String TOPIC = "rag.documents.ingestion.v1";

    @Mock
    private DocumentMetadataService metadataService;

    private DocumentSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new DocumentSyncConsumer(metadataService, objectMapper);
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

        consumer.consume(record);

        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(metadataService).processEvent(captor.capture());

        SyncEvent.Upsert event = assertInstanceOf(SyncEvent.Upsert.class, captor.getValue());
        assertEquals("doc-456", event.documentId());
        assertEquals("create", event.action());
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

        consumer.consume(record);

        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(metadataService).processEvent(captor.capture());

        SyncEvent.Delete event = assertInstanceOf(SyncEvent.Delete.class, captor.getValue());
        assertEquals("doc-456", event.documentId());
    }

    @Test
    void shouldSkipNullValueRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 3L, "doc-456", null);

        consumer.consume(record);

        verify(metadataService, never()).processEvent(any());
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
    }
}
