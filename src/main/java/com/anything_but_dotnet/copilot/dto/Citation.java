package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record Citation(
        @NotBlank @JsonProperty("title") String title,
        @NotBlank @JsonProperty("url") String url,
        @NotBlank @JsonProperty("document_id") String documentId,
        @NotBlank @JsonProperty("chunk_id") String chunkId,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @JsonProperty("score") Double score
) {}
