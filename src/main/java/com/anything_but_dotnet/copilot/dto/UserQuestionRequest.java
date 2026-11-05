package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserQuestionRequest(
        @NotBlank
        @Size(min = 1)
        @JsonProperty("query_id")
        String queryId,

        @NotNull
        @JsonProperty("channel")
        ChatChannel channel,

        @NotBlank
        @Email
        @JsonProperty("user_email")
        String userEmail,

        @NotBlank
        @Size(min = 1, max = 4000)
        @JsonProperty("query")
        String query
) {
}
