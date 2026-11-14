package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

            @NotNull
            @JsonProperty("outcome")
            GenerationOutcome outcome,

            @JsonProperty("error_category")
            ErrorCategory errorCategory,

            @DecimalMin("0.0")
            @DecimalMax("1.0")
            @JsonProperty("confidence_score")
            Double confidenceScore,

            @NotNull
            @JsonProperty("created_at")
            Instant createdAt
    ) {
        public static GenerationLogItem from(GenerationDiagnostics diag) {
            return new GenerationLogItem(
                    diag.getId(),
                    diag.getQueryId(),
                    diag.getOutcome(),
                    diag.getErrorCategory(),
                    diag.getConfidenceScore(),
                    diag.getCreatedAt()
            );
        }
    }
}
