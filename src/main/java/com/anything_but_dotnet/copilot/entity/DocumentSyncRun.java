package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.SyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "document_sync_runs")
public class DocumentSyncRun {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private SyncStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected DocumentSyncRun() {}

    public DocumentSyncRun(UUID id, String documentId, Instant startedAt) {
        this.id = id;
        this.documentId = documentId;
        this.status = SyncStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public void markCompleted(Instant finishedAt) {
        this.status = SyncStatus.COMPLETED;
        this.finishedAt = finishedAt;
    }

    public void markFailed(SyncStatus failedStatus, String errorMessage, Instant finishedAt) {
        this.status = failedStatus;
        this.errorMessage = errorMessage;
        this.finishedAt = finishedAt;
    }

    public UUID getId() { return id; }
    public String getDocumentId() { return documentId; }
    public SyncStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}