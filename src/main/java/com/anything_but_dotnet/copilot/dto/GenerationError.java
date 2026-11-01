package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerationError(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("status") ErrorCategory status,
        @JsonProperty("message") String message,
        @JsonProperty("description") String description,
        @JsonProperty("timestamp") Long timestamp
) {}