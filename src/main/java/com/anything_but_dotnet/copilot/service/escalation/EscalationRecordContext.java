package com.anything_but_dotnet.copilot.service.escalation;

import com.anything_but_dotnet.copilot.dto.Citation;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EscalationRecordContext(
        UUID escalationId,
        String queryId,
        String requesterEmail,
        String questionText,
        List<Citation> sourceReferences,
        ChatChannel channel,
        QuestionOutcomeReasonCode reasonCode,
        Double confidenceScore,
        Instant createdAt
) {
    public EscalationRecordContext {
        sourceReferences = sourceReferences != null ? List.copyOf(sourceReferences) : List.of();
    }
}
