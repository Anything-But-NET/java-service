package com.anything_but_dotnet.copilot.model;

public enum ErrorCategory {
    VALIDATION_ERROR,
    RETRIEVAL_TIMEOUT,
    VECTOR_DB_UNAVAILABLE,
    LLM_PROVIDER_ERROR,
    INTERNAL_ERROR,
    INTERNAL_CONNECTION_ERROR
}