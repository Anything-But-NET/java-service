package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagRetrievalRequest(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("query") String query,
        @JsonProperty("user_permissions") AclPayload userPermissions
) {}