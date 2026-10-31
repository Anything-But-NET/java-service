package com.anything_but_dotnet.copilot.dto;

public record GenerationError(
        String query_id,
        String status,
        String message,
        String description,
        Long timestamp
) {}