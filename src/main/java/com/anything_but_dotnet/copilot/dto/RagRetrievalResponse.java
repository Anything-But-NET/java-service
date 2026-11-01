package com.anything_but_dotnet.copilot.dto;

import com.anything_but_dotnet.copilot.model.GenerationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RagRetrievalResponse(
        @JsonProperty("query_id") String queryId,
        @JsonProperty("status") GenerationStatus status,
        @JsonProperty("answer") String answer,
        @JsonProperty("confidence_score") Double confidenceScore,
        @JsonProperty("citations") List<Citation> citations
) {}