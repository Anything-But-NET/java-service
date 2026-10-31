package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_sync_runs", schema = "core")
public class DocumentSyncRun {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    // Default constructor for JPA
    public DocumentSyncRun() {}

    public DocumentSyncRun(UUID id, String documentId, SyncStatus status, String errorMessage) {
        this.id = id;
        this.documentId = documentId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.startedAt = ZonedDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public SyncStatus getStatus() { return status; }
    public void setStatus(SyncStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public ZonedDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(ZonedDateTime startedAt) { this.startedAt = startedAt; }
    public ZonedDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(ZonedDateTime finishedAt) { this.finishedAt = finishedAt; }
}