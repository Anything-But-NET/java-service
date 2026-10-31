package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record KafkaSyncStatusEvent(
        @JsonProperty("sync_id") String syncId,
        @JsonProperty("document_id") String documentId,
        @JsonProperty("status") SyncStatus status,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("timestamp") Long timestamp
) {}
