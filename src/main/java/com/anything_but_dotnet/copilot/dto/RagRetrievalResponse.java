package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record RagRetrievalResponse(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("status") GenerationStatus status,
        @JsonProperty("answer") String answer,
        @JsonProperty("confidence_score") Double confidenceScore,
        @JsonProperty("prompt_version_id") UUID promptVersionId,
        @JsonProperty("citations") List<RagCitation> citations
) {
    public RagRetrievalResponse(
            String queryId,
            GenerationStatus status,
            String answer,
            Double confidenceScore,
            List<RagCitation> citations
    ) {
        this(queryId, status, answer, confidenceScore, null, citations);
    }
}
