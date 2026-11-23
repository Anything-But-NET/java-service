package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEscalationRequest(
        @NotBlank
        @Size(max = 255)
        @JsonProperty("query_id")
        String queryId
) {
}