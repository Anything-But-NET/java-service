package com.anything_but_dotnet.copilot.dto;

import java.util.List;

public record RagRetrievalResponse(
        String query_id,
        String status,
        String answer,
        Double confidence_score,
        List<Citation> citations
) {
    public record Citation(
            String title,
            String url,
            String document_id,
            String chunk_id,
            Double score
    ) {}
}