package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "generation_diagnostics", schema = "core")
public class GenerationDiagnostics {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "query_id", nullable = false)
    private String queryId;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public GenerationDiagnostics() {}

    public GenerationDiagnostics(String queryId, String outcome, String errorCategory, Double confidenceScore) {
        this.queryId = queryId;
        this.outcome = outcome;
        this.errorCategory = errorCategory;
        this.confidenceScore = confidenceScore;
    }

    // Getters
    public UUID getId() { return id; }
    public String getQueryId() { return queryId; }
    public String getOutcome() { return outcome; }
    public String getErrorCategory() { return errorCategory; }
    public Double getConfidenceScore() { return confidenceScore; }
}