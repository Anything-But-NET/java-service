package com.anything_but_dotnet.copilot.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentMetadataTest {

    @Test
    void shouldApplyUpsertAndDetectStaleEvents() {
        DocumentMetadata doc = DocumentMetadata.create("doc-123");
        Instant updatedTime = Instant.parse("2026-10-26T12:00:00Z");
        Instant syncTime = Instant.parse("2026-10-26T12:05:00Z");

        doc.applyUpsert(
                "sharepoint",
                "Title",
                "https://company.sharepoint.com/doc-123",
                updatedTime,
                List.of("devs", "all-employees"),
                syncTime
        );

        assertEquals("doc-123", doc.getDocumentId());
        assertEquals("sharepoint", doc.getSourceSystem());
        assertEquals("Title", doc.getTitle());
        assertEquals("https://company.sharepoint.com/doc-123", doc.getUrl());
        assertEquals(updatedTime, doc.getLastUpdated());
        assertEquals(List.of("devs", "all-employees"), doc.getAllowedGroups());
        assertEquals(syncTime, doc.getLastSyncAt());

        assertTrue(doc.isStale(Instant.parse("2026-10-26T11:59:59Z")));
        assertFalse(doc.isStale(Instant.parse("2026-10-26T12:00:00Z")));
        assertFalse(doc.isStale(Instant.parse("2026-10-26T12:00:01Z")));
    }

    @Test
    void shouldUpdateOnlyAclAndSyncTimestampOnApplyAcl() {
        DocumentMetadata doc = DocumentMetadata.create("doc-123");
        Instant initialUpdated = Instant.parse("2026-10-26T10:00:00Z");
        Instant initialSync = Instant.parse("2026-10-26T10:01:00Z");

        doc.applyUpsert(
                "sharepoint",
                "Original Title",
                "https://company.sharepoint.com/doc-123",
                initialUpdated,
                List.of("devs"),
                initialSync
        );

        Instant aclSyncTime = Instant.parse("2026-10-26T11:00:00Z");
        doc.applyAcl(List.of("devs", "managers"), aclSyncTime);

        assertEquals("Original Title", doc.getTitle());
        assertEquals("https://company.sharepoint.com/doc-123", doc.getUrl());
        assertEquals(initialUpdated, doc.getLastUpdated());
        assertEquals(List.of("devs", "managers"), doc.getAllowedGroups());
        assertEquals(aclSyncTime, doc.getLastSyncAt());
    }

    @Test
    void shouldReturnUnmodifiableAllowedGroups() {
        DocumentMetadata doc = DocumentMetadata.create("doc-123");
        doc.applyAcl(List.of("devs"), Instant.parse("2026-10-26T10:00:00Z"));

        assertThrows(UnsupportedOperationException.class, () -> doc.getAllowedGroups().add("hackers"));
    }
}
