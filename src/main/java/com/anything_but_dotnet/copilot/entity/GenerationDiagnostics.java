package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "generation_diagnostics")
public class GenerationDiagnostics {

    @Id
    private UUID id;

    @Column(name = "query_id", nullable = false)
    private String queryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category")
    private ErrorCategory errorCategory;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GenerationDiagnostics() {}

    private GenerationDiagnostics(UUID id, String queryId, GenerationOutcome outcome,
                                  ErrorCategory errorCategory, Double confidenceScore, Instant createdAt) {
        this.id = id;
        this.queryId = queryId;
        this.outcome = outcome;
        this.errorCategory = errorCategory;
        this.confidenceScore = confidenceScore;
        this.createdAt = createdAt;
    }

    public static GenerationDiagnostics success(UUID id, String queryId, Double confidenceScore, Instant createdAt) {
        return new GenerationDiagnostics(id, queryId, GenerationOutcome.SUCCESS, null, confidenceScore, createdAt);
    }

    public static GenerationDiagnostics failure(UUID id, String queryId, ErrorCategory errorCategory, Instant createdAt) {
        return new GenerationDiagnostics(id, queryId, GenerationOutcome.FAILURE, errorCategory, null, createdAt);
    }

    public UUID getId() { return id; }
    public String getQueryId() { return queryId; }
    public GenerationOutcome getOutcome() { return outcome; }
    public ErrorCategory getErrorCategory() { return errorCategory; }
    public Double getConfidenceScore() { return confidenceScore; }
    public Instant getCreatedAt() { return createdAt; }
}