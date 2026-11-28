package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Log item DTOs for {@code /api/v1/dash/logs/*}.
 */
public interface DashboardLogDto {

    record IngestionLogItem(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @NotBlank
            @JsonProperty("document_id")
            String documentId,

            @NotNull
            @JsonProperty("status")
            SyncStatus status,

            @JsonProperty("error_message")
            String errorMessage,

            @NotNull
            @JsonProperty("started_at")
            Instant startedAt,

            @JsonProperty("finished_at")
            Instant finishedAt
    ) {
        public static IngestionLogItem from(DocumentSyncRun run) {
            return new IngestionLogItem(
                    run.getId(),
                    run.getDocumentId(),
                    run.getStatus(),
                    run.getErrorMessage(),
                    run.getStartedAt(),
                    run.getFinishedAt()
            );
        }
    }

    record GenerationLogItem(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @NotBlank
            @JsonProperty("query_id")
            String queryId,

            @JsonProperty("channel")
            ChatChannel channel,

            @NotNull
            @JsonProperty("outcome")
            GenerationOutcome outcome,

            @JsonProperty("reason_code")
            QuestionOutcomeReasonCode reasonCode,

            @JsonProperty("error_category")
            ErrorCategory errorCategory,

            @DecimalMin("0.0")
            @DecimalMax("1.0")
            @JsonProperty("confidence_score")
            Double confidenceScore,

            @Min(0)
            @JsonProperty("latency_ms")
            Integer latencyMs,

            @JsonProperty("prompt_version_id")
            UUID promptVersionId,

            @JsonProperty("escalation_id")
            UUID escalationId,

            @JsonProperty("resolution_status")
            EscalationStatus resolutionStatus,

            @JsonProperty("resolution_id")
            UUID resolutionId,

            @JsonProperty("resolved_at")
            Instant resolvedAt,

            @NotNull
            @JsonProperty("created_at")
            Instant createdAt,

            @JsonProperty("updated_at")
            Instant updatedAt
    ) {
        public GenerationLogItem(
                UUID id,
                String queryId,
                ChatChannel channel,
                GenerationOutcome outcome,
                QuestionOutcomeReasonCode reasonCode,
                ErrorCategory errorCategory,
                Double confidenceScore,
                Integer latencyMs,
                UUID promptVersionId,
                UUID escalationId,
                EscalationStatus resolutionStatus,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(id, queryId, channel, outcome, reasonCode, errorCategory, confidenceScore, latencyMs,
                    promptVersionId, escalationId, resolutionStatus, null, null, createdAt, updatedAt);
        }

        public static GenerationLogItem from(GenerationDiagnostics diag) {
            return from(diag, null);
        }

        public static GenerationLogItem from(GenerationDiagnostics diag, Escalation escalation) {
            return new GenerationLogItem(
                    diag.getId(),
                    diag.getQueryId(),
                    diag.getChannel(),
                    diag.getOutcome(),
                    diag.getReasonCode(),
                    diag.getErrorCategory(),
                    diag.getConfidenceScore(),
                    diag.getLatencyMs(),
                    diag.getPromptVersionId(),
                    escalation != null ? escalation.getId() : null,
                    escalation != null ? escalation.getStatus() : null,
                    escalation != null ? escalation.getResolutionId() : null,
                    escalation != null ? escalation.getResolvedAt() : null,
                    diag.getCreatedAt(),
                    diag.getUpdatedAt()
            );
        }
    }
}