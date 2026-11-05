package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.GatewayErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GatewayErrorResponse(
        @JsonProperty("query_id")
        String queryId,

        @NotNull
        @JsonProperty("error_code")
        GatewayErrorCode errorCode,

        @NotBlank
        @JsonProperty("user_message")
        String userMessage
) {
}
