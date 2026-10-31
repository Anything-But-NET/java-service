package com.anything_but_dotnet.copilot.dto;

import java.util.List;

public record RagRetrievalRequest(
        String query_id,
        String query,
        AclPayload user_permissions
) {
    public record AclPayload(List<String> allowed_groups) {}
}