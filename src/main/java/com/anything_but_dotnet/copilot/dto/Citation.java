package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Citation(
        @JsonProperty("title") String title,
        @JsonProperty("url") String url,
        @JsonProperty("document_id") String documentId,
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("score") Double score
) {}