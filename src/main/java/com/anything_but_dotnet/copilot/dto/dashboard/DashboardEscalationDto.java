package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.dto.Citation;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

            @NotBlank
            @JsonProperty("requester_email")
            String requesterEmail,

            @NotBlank
            @JsonProperty("question_text")
            String questionText,

            @NotNull
            @JsonProperty("status")
            EscalationStatus status,

            @JsonProperty("resolution_text")
            String resolutionText,

            @JsonProperty("resolution_updated_at")
            Instant resolutionUpdatedAt,

            @JsonProperty("resolved_by_operator_id")
            UUID resolvedByOperatorId,

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
        public EscalationItem(
                UUID id,
                String queryId,
                String requesterEmail,
                String questionText,
                EscalationStatus status,
                ChatChannel channel,
                QuestionOutcomeReasonCode reasonCode,
                Double confidenceScore,
                Instant createdAt,
                Instant updatedAt,
                Instant resolvedAt
        ) {
            this(id, queryId, requesterEmail, questionText, status, null, null, null,
                    channel, reasonCode, confidenceScore, createdAt, updatedAt, resolvedAt);
        }

        public static EscalationItem from(Escalation escalation, GenerationDiagnostics diag) {
            return new EscalationItem(
                    escalation.getId(),
                    escalation.getQueryId(),
                    escalation.getRequesterEmail(),
                    escalation.getQuestionText(),
                    escalation.getStatus(),
                    escalation.getResolutionText(),
                    escalation.getResolutionUpdatedAt(),
                    escalation.getResolvedByOperatorId(),
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

            @NotBlank
            @JsonProperty("requester_email")
            String requesterEmail,

            @NotBlank
            @JsonProperty("question_text")
            String questionText,

            @NotNull
            @JsonProperty("source_references")
            List<Citation> sourceReferences,

            @NotNull
            @JsonProperty("status")
            EscalationStatus status,

            @JsonProperty("resolution_text")
            String resolutionText,

            @JsonProperty("resolution_updated_at")
            Instant resolutionUpdatedAt,

            @JsonProperty("resolved_by_operator_id")
            UUID resolvedByOperatorId,

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
        public EscalationDetail(
                UUID id,
                String queryId,
                String requesterEmail,
                String questionText,
                List<Citation> sourceReferences,
                EscalationStatus status,
                ChatChannel channel,
                QuestionOutcomeReasonCode reasonCode,
                Double confidenceScore,
                Instant createdAt,
                Instant updatedAt,
                Instant resolvedAt,
                List<EscalationStatusHistoryItem> statusHistory
        ) {
            this(id, queryId, requesterEmail, questionText, sourceReferences, status, null, null, null,
                    channel, reasonCode, confidenceScore, createdAt, updatedAt, resolvedAt, statusHistory);
        }

        public static EscalationDetail from(
                Escalation escalation,
                GenerationDiagnostics diag,
                List<Citation> sourceReferences,
                List<EscalationStatusHistory> history
        ) {
            return new EscalationDetail(
                    escalation.getId(),
                    escalation.getQueryId(),
                    escalation.getRequesterEmail(),
                    escalation.getQuestionText(),
                    sourceReferences != null ? sourceReferences : List.of(),
                    escalation.getStatus(),
                    escalation.getResolutionText(),
                    escalation.getResolutionUpdatedAt(),
                    escalation.getResolvedByOperatorId(),
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

    record SaveEscalationResponseRequest(
            @NotBlank
            @Size(max = 20000)
            @JsonProperty("resolution_text")
            String resolutionText,

            @JsonProperty("status")
            EscalationStatus status
    ) {
    }
}