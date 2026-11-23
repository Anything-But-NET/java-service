package com.anything_but_dotnet.copilot.exception;

public class EscalationDraftExpiredException extends RuntimeException {

    public EscalationDraftExpiredException(String message) {
        super(message);
    }
}