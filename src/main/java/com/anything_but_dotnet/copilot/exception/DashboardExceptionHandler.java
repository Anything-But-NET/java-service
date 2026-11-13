package com.anything_but_dotnet.copilot.exception;

import com.anything_but_dotnet.copilot.controllers.AuthController;
import com.anything_but_dotnet.copilot.controllers.DashboardController;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.ErrorResponse;
import com.anything_but_dotnet.copilot.model.DashboardErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;

@RestControllerAdvice(assignableTypes = {AuthController.class, DashboardController.class})
public class DashboardExceptionHandler {

    private final Clock clock;

    public DashboardExceptionHandler(ObjectProvider<Clock> clockProvider) {
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        String message = switch (ex) {
            case MethodArgumentNotValidException validEx -> validEx.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .orElse("Request validation failed.");
            case MethodArgumentTypeMismatchException mismatchEx ->
                    "Invalid parameter '" + mismatchEx.getName() + "'.";
            case HttpMessageNotReadableException ignored -> "Malformed JSON request body.";
            default -> nonBlankOrDefault(ex.getMessage(), "Invalid request parameters.");
        };
        return build(HttpStatus.BAD_REQUEST, DashboardErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({LockedException.class, DisabledException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(Exception ex) {
        return build(
                HttpStatus.FORBIDDEN,
                DashboardErrorCode.FORBIDDEN,
                nonBlankOrDefault(ex.getMessage(), "Operator account is BLOCKED or lacks required authority.")
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(AuthenticationException ex) {
        return build(
                HttpStatus.UNAUTHORIZED,
                DashboardErrorCode.UNAUTHORIZED,
                nonBlankOrDefault(ex.getMessage(), "Invalid credentials or token.")
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return build(
                HttpStatus.NOT_FOUND,
                DashboardErrorCode.NOT_FOUND,
                nonBlankOrDefault(ex.getMessage(), "Requested resource was not found.")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                DashboardErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred."
        );
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, DashboardErrorCode code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(code, message, clock.instant()));
    }

    private static String nonBlankOrDefault(String message, String fallback) {
        return message != null && !message.isBlank() ? message : fallback;
    }
}
