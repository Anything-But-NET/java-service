package com.anything_but_dotnet.copilot.exception;

public class RagGenerationException extends RuntimeException {
    private final String errorCategory;

    public RagGenerationException(String errorCategory, String message) {
        super(message);
        this.errorCategory = errorCategory;
    }

    public String getErrorCategory() {
        return errorCategory;
    }
}