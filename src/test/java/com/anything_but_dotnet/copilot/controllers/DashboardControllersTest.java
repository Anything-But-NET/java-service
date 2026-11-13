package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.config.SecurityConfig;
import com.anything_but_dotnet.copilot.dto.auth.AccessTokenPayload;
import com.anything_but_dotnet.copilot.dto.auth.RefreshTokenPayload;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.OperatorItem;
import com.anything_but_dotnet.copilot.exception.DashboardExceptionHandler;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.security.AuthFilter;
import com.anything_but_dotnet.copilot.security.DashboardSecurityHandlers;
import com.anything_but_dotnet.copilot.security.JwtService;
import com.anything_but_dotnet.copilot.security.OperatorDetailsImpl;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import com.anything_but_dotnet.copilot.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, DashboardController.class})
@Import({
        SecurityConfig.class,
        AuthFilter.class,
        DashboardExceptionHandler.class,
        JwtService.class
})
class DashboardControllersTest {

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private DashboardOperatorService operatorService;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void loginReturnsAccessAndRefreshTokensForActiveOperator() throws Exception {
        String encoded = passwordEncoder.encode("secret123");
        var details = new OperatorDetailsImpl(
                "admin@company.com",
                DashboardOperatorStatus.ACTIVE,
                encoded,
                List.of(DashboardOperatorAuthority.LOGS, DashboardOperatorAuthority.OPERATORS)
        );
        when(userDetailsService.loadUserByUsername("admin@company.com")).thenReturn(details);

        mockMvc.perform(post("/api/v1/dash/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@company.com", "password": "secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.access_expires_in").value(600))
                .andExpect(jsonPath("$.refresh_token").isString())
                .andExpect(jsonPath("$.refresh_expires_in").value(5184000));
    }

    @Test
    void loginReturns403ForbiddenForBlockedOperator() throws Exception {
        String encoded = passwordEncoder.encode("secret123");
        var blocked = new OperatorDetailsImpl(
                "blocked@company.com",
                DashboardOperatorStatus.BLOCKED,
                encoded,
                List.of(DashboardOperatorAuthority.LOGS)
        );
        when(userDetailsService.loadUserByUsername("blocked@company.com")).thenReturn(blocked);

        mockMvc.perform(post("/api/v1/dash/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "blocked@company.com", "password": "secret123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void refreshReturns403WhenOperatorWasBlockedAnd200WhenActive() throws Exception {
        String refreshToken = jwtService.generateRefreshToken(new RefreshTokenPayload("admin@company.com"));
        Instant now = Instant.parse("2026-11-13T18:00:00Z");
        OperatorItem activeOperator = new OperatorItem(
                UUID.randomUUID(),
                "admin@company.com",
                List.of(DashboardOperatorAuthority.LOGS),
                DashboardOperatorStatus.ACTIVE,
                now, now, now
        );
        when(operatorService.refreshSession("admin@company.com"))
                .thenReturn(activeOperator)
                .thenThrow(new LockedException("Operator account is BLOCKED."));

        mockMvc.perform(post("/api/v1/dash/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.refresh_token").isString());

        mockMvc.perform(post("/api/v1/dash/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refresh_token": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void filterReturns401ForMissingOrMalformedJwtAnd403ForBlockedOrMissingAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/dash/logs/ingestion"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/dash/logs/ingestion")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        String blockedJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "blocked@company.com",
                DashboardOperatorStatus.BLOCKED,
                List.of(DashboardOperatorAuthority.LOGS)
        ));
        mockMvc.perform(get("/api/v1/dash/logs/ingestion")
                        .header("Authorization", "Bearer " + blockedJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        String metadataOnlyJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "meta@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.METADATA)
        ));
        mockMvc.perform(get("/api/v1/dash/logs/ingestion")
                        .header("Authorization", "Bearer " + metadataOnlyJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void ingestionLogsReturnPaginatedIsoTimestampsAndValidateParams() throws Exception {
        String logsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "logs@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.LOGS)
        ));
        Instant started = Instant.parse("2026-11-13T12:00:00Z");
        var page = new PageResponse<>(
                List.of(new IngestionLogItem(
                        UUID.randomUUID(),
                        "doc-456",
                        SyncStatus.COMPLETED,
                        null,
                        started,
                        started.plusSeconds(5)
                )),
                0, 20, 1L, 1
        );
        when(dashboardService.getIngestionLogs(eq(0), eq(20), any(), any(), eq(SyncStatus.COMPLETED), eq("doc-456")))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/dash/logs/ingestion")
                        .header("Authorization", "Bearer " + logsJwt)
                        .param("status", "COMPLETED")
                        .param("document_id", "doc-456")
                        .param("from", "2026-11-13T00:00:00Z")
                        .param("to", "2026-11-13T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].document_id").value("doc-456"))
                .andExpect(jsonPath("$.items[0].started_at").value("2026-11-13T12:00:00Z"));

        mockMvc.perform(get("/api/v1/dash/logs/ingestion")
                        .header("Authorization", "Bearer " + logsJwt)
                        .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));
    }

    @Test
    void patchOperatorStatusReturns200And404WhenMissing() throws Exception {
        String operatorsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "ops@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.OPERATORS)
        ));
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T18:00:00Z");
        OperatorItem updated = new OperatorItem(
                id,
                "target@company.com",
                List.of(DashboardOperatorAuthority.LOGS),
                DashboardOperatorStatus.BLOCKED,
                now, now, now
        );
        when(operatorService.updateStatus(id, DashboardOperatorStatus.BLOCKED)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/dash/operators/{id}/status", id)
                        .header("Authorization", "Bearer " + operatorsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "BLOCKED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        UUID missingId = UUID.randomUUID();
        when(operatorService.updateStatus(missingId, DashboardOperatorStatus.ACTIVE))
                .thenThrow(new NotFoundException("Operator not found"));

        mockMvc.perform(patch("/api/v1/dash/operators/{id}/status", missingId)
                        .header("Authorization", "Bearer " + operatorsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }
}
