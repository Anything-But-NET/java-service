package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagCitation(
        @JsonProperty("document_id") String documentId,
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("score") Double score
) {
}
