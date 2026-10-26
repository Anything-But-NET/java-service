package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncEvent(
        @JsonProperty("doc_id") String docId,
        @JsonProperty("action") String action,
        @JsonProperty("roles") List<String> roles
) {}