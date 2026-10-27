package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void shouldDeserializeUpsertEvent(String action) throws Exception {
        String json = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "%s",
                  "source_system": "sharepoint",
                  "title": "IT Compensation Guidelines",
                  "url": "https://company.sharepoint.com/docs/it-comp",
                  "raw_content": "Full plain text payload",
                  "last_updated": "2026-10-26T16:00:00Z",
                  "acl": {
                    "allowed_groups": ["devs", " all-employees ", "devs", ""]
                  }
                }
                """.formatted(action);

        SyncEvent event = objectMapper.readValue(json, SyncEvent.class);

        SyncEvent.Upsert upsert = assertInstanceOf(SyncEvent.Upsert.class, event);
        assertEquals("doc-456", upsert.documentId());
        assertEquals(action, upsert.action());
        assertEquals("sharepoint", upsert.sourceSystem());
        assertEquals("IT Compensation Guidelines", upsert.title());
        assertEquals("https://company.sharepoint.com/docs/it-comp", upsert.url());
        assertEquals(Instant.parse("2026-10-26T16:00:00Z"), upsert.lastUpdated());
        assertEquals(List.of("devs", "all-employees"), upsert.allowedGroups());
    }

    @Test
    void shouldDeserializeUpdateAclEvent() throws Exception {
        String json = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "update_acl",
                  "acl": {
                    "allowed_groups": ["Engineering - Backend"]
                  }
                }
                """;

        SyncEvent event = objectMapper.readValue(json, SyncEvent.class);

        SyncEvent.UpdateAcl updateAcl = assertInstanceOf(SyncEvent.UpdateAcl.class, event);
        assertEquals("doc-456", updateAcl.documentId());
        assertEquals(List.of("Engineering - Backend"), updateAcl.allowedGroups());
    }

    @Test
    void shouldDeserializeDeleteEvent() throws Exception {
        String json = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "delete"
                }
                """;

        SyncEvent event = objectMapper.readValue(json, SyncEvent.class);

        SyncEvent.Delete delete = assertInstanceOf(SyncEvent.Delete.class, event);
        assertEquals("doc-456", delete.documentId());
    }

    @Test
    void shouldRejectUnknownAction() {
        String json = """
                {
                  "sync_id": "547f7ca3-1eaa-44c6-91ed-bd8f8e997cf8",
                  "document_id": "doc-456",
                  "action": "unknown_action"
                }
                """;

        assertThrows(JsonProcessingException.class, () -> objectMapper.readValue(json, SyncEvent.class));
    }
}
