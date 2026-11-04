package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(schema = "core", name = "document_metadata")
public class DocumentMetadata {

    @Id
    @Column(name = "document_id", nullable = false, updatable = false)
    private String documentId;

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;

    protected DocumentMetadata() {
    }

    private DocumentMetadata(String documentId) {
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
    }

    public static DocumentMetadata create(String documentId) {
        return new DocumentMetadata(documentId);
    }

    public boolean isStale(Instant incomingLastUpdated) {
        return this.lastUpdated != null
                && incomingLastUpdated != null
                && incomingLastUpdated.isBefore(this.lastUpdated);
    }

    public void applyUpsert(
            String sourceSystem,
            String title,
            String url,
            Instant lastUpdated,
            List<String> allowedGroups,
            Instant syncedAt
    ) {
        this.sourceSystem = sourceSystem;
        this.title = title;
        this.url = url;
        this.lastUpdated = lastUpdated;
        this.lastSyncAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
    }

    public void applyAcl(Instant syncedAt) {
        this.lastSyncAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }
}
