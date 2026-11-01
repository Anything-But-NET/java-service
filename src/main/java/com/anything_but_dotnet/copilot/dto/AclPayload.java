package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public record AclPayload(
        @JsonProperty("allowed_groups") List<String> allowedGroups
) {
    public List<String> normalizedGroups() {
        if (allowedGroups == null || allowedGroups.isEmpty()) {
            return List.of();
        }
        return allowedGroups.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}