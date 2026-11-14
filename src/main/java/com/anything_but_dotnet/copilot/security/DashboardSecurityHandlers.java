package com.anything_but_dotnet.copilot.security;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto;
import com.anything_but_dotnet.copilot.model.DashboardErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Renders standardized {@link DashboardCommonDto.ErrorResponse} JSON for filter-level
 * authentication (401) and authorization (403) failures.
 */
public class DashboardSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DashboardSecurityHandlers(
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        ObjectMapper baseMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.objectMapper = baseMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String message = authException != null && authException.getMessage() != null && !authException.getMessage().isBlank()
                ? authException.getMessage()
                : "Missing or expired Bearer access token.";
        writeError(response, HttpStatus.UNAUTHORIZED, DashboardErrorCode.UNAUTHORIZED, message);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        String message = accessDeniedException != null
                && accessDeniedException.getMessage() != null
                && !accessDeniedException.getMessage().isBlank()
                ? accessDeniedException.getMessage()
                : "Missing required authority or operator is BLOCKED.";
        writeError(response, HttpStatus.FORBIDDEN, DashboardErrorCode.FORBIDDEN, message);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            DashboardErrorCode errorCode,
            String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var payload = DashboardCommonDto.ErrorResponse.of(errorCode, message, clock.instant());
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }
}
