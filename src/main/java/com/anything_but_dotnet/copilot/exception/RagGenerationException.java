package com.anything_but_dotnet.copilot.exception;

import com.anything_but_dotnet.copilot.model.ErrorCategory;

public class RagGenerationException extends RuntimeException {

    private final ErrorCategory errorCategory;

    public RagGenerationException(String message, ErrorCategory errorCategory) {
        super(message);
        this.errorCategory = errorCategory;
    }

    public RagGenerationException(String message, Throwable cause, ErrorCategory errorCategory) {
        super(message, cause);
        this.errorCategory = errorCategory;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }
}