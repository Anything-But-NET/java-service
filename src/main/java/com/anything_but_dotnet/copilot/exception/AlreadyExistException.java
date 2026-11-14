package com.anything_but_dotnet.copilot.exception;

public class AlreadyExistException extends RuntimeException {
    public AlreadyExistException(String message) {
        super(message);
    }
    public AlreadyExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
