package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record EscalationResponse(
        @NotNull
        @JsonProperty("escalation_id")
        UUID escalationId,

        @NotBlank
        @JsonProperty("query_id")
        String queryId,

        @NotNull
        @JsonProperty("status")
        EscalationStatus status,

        @NotNull
        @JsonProperty("created_at")
        Instant createdAt,

        @NotNull
        @JsonProperty("updated_at")
        Instant updatedAt
) {
    public static EscalationResponse from(Escalation escalation) {
        return new EscalationResponse(
                escalation.getId(),
                escalation.getQueryId(),
                escalation.getStatus(),
                escalation.getCreatedAt(),
                escalation.getUpdatedAt()
        );
    }
}