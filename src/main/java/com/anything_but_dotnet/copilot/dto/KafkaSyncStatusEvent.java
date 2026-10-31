package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.entity.SyncStatus;
import java.util.UUID;

public record KafkaSyncStatusEvent(
        UUID sync_id,
        String document_id,
        SyncStatus status,
        String error_message,
        Long timestamp
) {}
