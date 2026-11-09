package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserAclUpsert(
        String email,
        AclPayload acl,
        @JsonProperty("last_updated") Instant lastUpdated
) {
}
