package com.anything_but_dotnet.copilot.exception;

import com.anything_but_dotnet.copilot.controllers.GenerationController;
import com.anything_but_dotnet.copilot.dto.GatewayErrorResponse;
import com.anything_but_dotnet.copilot.dto.UserQuestionRequest;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GatewayErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@RestControllerAdvice(assignableTypes = GenerationController.class)
public class GatewayExceptionHandler extends RequestBodyAdviceAdapter {

    public static final String QUERY_ID_ATTRIBUTE = "gateway.query_id";

    private static final String BAD_REQUEST_MESSAGE =
            "Your question is empty or exceeds the maximum allowed length.";
    private static final String SERVICE_UNAVAILABLE_MESSAGE =
            "The knowledge assistant is temporarily unavailable. Please try again in a few minutes.";
    private static final String INTERNAL_ERROR_MESSAGE =
            "An unexpected error occurred while processing your request.";

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return UserQuestionRequest.class.equals(targetType);
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        if (body instanceof UserQuestionRequest request && request.queryId() != null && !request.queryId().isBlank()) {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute(QUERY_ID_ATTRIBUTE, request.queryId(), RequestAttributes.SCOPE_REQUEST);
            }
        }
        return body;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GatewayErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String queryId = resolveQueryId(request);
        if (queryId == null && ex.getBindingResult().getTarget() instanceof UserQuestionRequest target) {
            if (target.queryId() != null && !target.queryId().isBlank()) {
                queryId = target.queryId();
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GatewayErrorResponse(
                        queryId,
                        GatewayErrorCode.BAD_REQUEST,
                        BAD_REQUEST_MESSAGE
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GatewayErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException _ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GatewayErrorResponse(
                        resolveQueryId(request),
                        GatewayErrorCode.BAD_REQUEST,
                        BAD_REQUEST_MESSAGE
                ));
    }

    @ExceptionHandler(RagGenerationException.class)
    public ResponseEntity<GatewayErrorResponse> handleRagGenerationException(
            RagGenerationException ex,
            HttpServletRequest request
    ) {
        String queryId = resolveQueryId(request);
        ErrorCategory category = ex.getErrorCategory();

        if (category == ErrorCategory.RETRIEVAL_TIMEOUT
                || category == ErrorCategory.VECTOR_DB_UNAVAILABLE
                || category == ErrorCategory.LLM_PROVIDER_ERROR
                || category == ErrorCategory.INTERNAL_CONNECTION_ERROR) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new GatewayErrorResponse(
                            queryId,
                            GatewayErrorCode.SERVICE_UNAVAILABLE,
                            SERVICE_UNAVAILABLE_MESSAGE
                    ));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GatewayErrorResponse(
                        queryId,
                        GatewayErrorCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_MESSAGE
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<GatewayErrorResponse> handleDatabaseException(
            DataAccessException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new GatewayErrorResponse(
                        resolveQueryId(request),
                        GatewayErrorCode.SERVICE_UNAVAILABLE,
                        SERVICE_UNAVAILABLE_MESSAGE
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GatewayErrorResponse(
                        resolveQueryId(request),
                        GatewayErrorCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_MESSAGE
                ));
    }

    private String resolveQueryId(HttpServletRequest request) {
        Object attr = request.getAttribute(QUERY_ID_ATTRIBUTE);
        return attr instanceof String s ? s : null;
    }
}
