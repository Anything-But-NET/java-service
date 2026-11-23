package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Escalation queue DTOs for {@code /api/v1/dash/escalations/*}.
 */
public interface DashboardEscalationDto {

    record EscalationItem(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @NotBlank
            @JsonProperty("query_id")
            String queryId,

            @NotNull
            @JsonProperty("status")
            EscalationStatus status,

            @JsonProperty("channel")
            ChatChannel channel,

            @JsonProperty("reason_code")
            QuestionOutcomeReasonCode reasonCode,

            @JsonProperty("confidence_score")
            Double confidenceScore,

            @NotNull
            @JsonProperty("created_at")
            Instant createdAt,

            @NotNull
            @JsonProperty("updated_at")
            Instant updatedAt,

            @JsonProperty("resolved_at")
            Instant resolvedAt
    ) {
        public static EscalationItem from(Escalation escalation, GenerationDiagnostics diag) {
            return new EscalationItem(
                    escalation.getId(),
                    escalation.getQueryId(),
                    escalation.getStatus(),
                    diag != null ? diag.getChannel() : null,
                    diag != null ? diag.getReasonCode() : null,
                    diag != null ? diag.getConfidenceScore() : null,
                    escalation.getCreatedAt(),
                    escalation.getUpdatedAt(),
                    escalation.getResolvedAt()
            );
        }
    }

    record EscalationStatusHistoryItem(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @JsonProperty("from_status")
            EscalationStatus fromStatus,

            @NotNull
            @JsonProperty("to_status")
            EscalationStatus toStatus,

            @JsonProperty("changed_by_operator_id")
            UUID changedByOperatorId,

            @NotNull
            @JsonProperty("changed_at")
            Instant changedAt
    ) {
        public static EscalationStatusHistoryItem from(EscalationStatusHistory entry) {
            return new EscalationStatusHistoryItem(
                    entry.getId(),
                    entry.getFromStatus(),
                    entry.getToStatus(),
                    entry.getChangedByOperatorId(),
                    entry.getChangedAt()
            );
        }
    }

    record EscalationDetail(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @NotBlank
            @JsonProperty("query_id")
            String queryId,

            @NotNull
            @JsonProperty("status")
            EscalationStatus status,

            @JsonProperty("channel")
            ChatChannel channel,

            @JsonProperty("reason_code")
            QuestionOutcomeReasonCode reasonCode,

            @JsonProperty("confidence_score")
            Double confidenceScore,

            @NotNull
            @JsonProperty("created_at")
            Instant createdAt,

            @NotNull
            @JsonProperty("updated_at")
            Instant updatedAt,

            @JsonProperty("resolved_at")
            Instant resolvedAt,

            @NotNull
            @JsonProperty("status_history")
            List<EscalationStatusHistoryItem> statusHistory
    ) {
        public static EscalationDetail from(
                Escalation escalation,
                GenerationDiagnostics diag,
                List<EscalationStatusHistory> history
        ) {
            return new EscalationDetail(
                    escalation.getId(),
                    escalation.getQueryId(),
                    escalation.getStatus(),
                    diag != null ? diag.getChannel() : null,
                    diag != null ? diag.getReasonCode() : null,
                    diag != null ? diag.getConfidenceScore() : null,
                    escalation.getCreatedAt(),
                    escalation.getUpdatedAt(),
                    escalation.getResolvedAt(),
                    history.stream().map(EscalationStatusHistoryItem::from).toList()
            );
        }
    }

    record UpdateEscalationStatusRequest(
            @NotNull
            @JsonProperty("status")
            EscalationStatus status
    ) {
    }
}