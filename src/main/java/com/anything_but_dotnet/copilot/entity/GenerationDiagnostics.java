package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
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
@Table(schema = "core", name = "generation_diagnostics")
public class GenerationDiagnostics {

    @Id
    private UUID id;

    @Column(name = "query_id", nullable = false, unique = true)
    private String queryId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private ChatChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private GenerationOutcome outcome;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "reason_code")
    private QuestionOutcomeReasonCode reasonCode;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "error_category")
    private ErrorCategory errorCategory;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "prompt_version_id")
    private UUID promptVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GenerationDiagnostics() {}

    private GenerationDiagnostics(
            UUID id,
            String queryId,
            ChatChannel channel,
            GenerationOutcome outcome,
            QuestionOutcomeReasonCode reasonCode,
            ErrorCategory errorCategory,
            Double confidenceScore,
            Integer latencyMs,
            UUID promptVersionId,
            Instant createdAt
    ) {
        this.id = id;
        this.queryId = queryId;
        this.channel = channel != null ? channel : ChatChannel.SLACK;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.errorCategory = errorCategory;
        this.confidenceScore = confidenceScore;
        this.latencyMs = latencyMs;
        this.promptVersionId = promptVersionId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static GenerationDiagnostics answered(
            UUID id,
            String queryId,
            ChatChannel channel,
            Double confidenceScore,
            Integer latencyMs,
            UUID promptVersionId,
            Instant createdAt
    ) {
        return new GenerationDiagnostics(
                id, queryId, channel, GenerationOutcome.ANSWERED, null, null,
                confidenceScore, latencyMs, promptVersionId, createdAt
        );
    }

    public static GenerationDiagnostics safeNoAnswer(
            UUID id,
            String queryId,
            ChatChannel channel,
            QuestionOutcomeReasonCode reasonCode,
            Double confidenceScore,
            Integer latencyMs,
            UUID promptVersionId,
            Instant createdAt
    ) {
        return new GenerationDiagnostics(
                id, queryId, channel, GenerationOutcome.SAFE_NO_ANSWER, reasonCode, null,
                confidenceScore, latencyMs, promptVersionId, createdAt
        );
    }

    public static GenerationDiagnostics failure(
            UUID id,
            String queryId,
            ChatChannel channel,
            ErrorCategory errorCategory,
            Integer latencyMs,
            UUID promptVersionId,
            Instant createdAt
    ) {
        return new GenerationDiagnostics(
                id, queryId, channel, GenerationOutcome.FAILURE, null, errorCategory,
                null, latencyMs, promptVersionId, createdAt
        );
    }

    public void markEscalated(Instant updatedAt) {
        if (this.outcome == GenerationOutcome.ESCALATED) {
            return;
        }
        if (this.outcome == GenerationOutcome.ANSWERED) {
            this.reasonCode = null;
        }
        this.outcome = GenerationOutcome.ESCALATED;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getQueryId() { return queryId; }
    public ChatChannel getChannel() { return channel; }
    public GenerationOutcome getOutcome() { return outcome; }
    public QuestionOutcomeReasonCode getReasonCode() { return reasonCode; }
    public ErrorCategory getErrorCategory() { return errorCategory; }
    public Double getConfidenceScore() { return confidenceScore; }
    public Integer getLatencyMs() { return latencyMs; }
    public UUID getPromptVersionId() { return promptVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
