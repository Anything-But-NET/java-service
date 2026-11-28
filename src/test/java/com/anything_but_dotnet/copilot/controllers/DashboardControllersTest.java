package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.config.SecurityConfig;
import com.anything_but_dotnet.copilot.dto.auth.AccessTokenPayload;
import com.anything_but_dotnet.copilot.dto.auth.RefreshTokenPayload;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ChannelCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ConfidenceBuckets;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.OutcomeCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.OperatorItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationDetail;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationStatusHistoryItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.DailyEscalationPoint;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.EscalationAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.EscalationStatusCount;
import com.anything_but_dotnet.copilot.dto.Citation;
import com.anything_but_dotnet.copilot.exception.DashboardExceptionHandler;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.security.AuthFilter;
import com.anything_but_dotnet.copilot.security.JwtService;
import com.anything_but_dotnet.copilot.security.OperatorDetailsImpl;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import com.anything_but_dotnet.copilot.service.DashboardService;
import com.anything_but_dotnet.copilot.service.EscalationService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, DashboardController.class, InternalAuthController.class})
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

    @MockitoBean
    private EscalationService escalationService;

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
    void analyticsEndpointsEnforceRbacAndBindLowercaseChannel() throws Exception {
        Instant from = Instant.parse("2026-11-18T00:00:00Z");
        Instant to = Instant.parse("2026-11-21T00:00:00Z");
        UUID promptId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        QuestionOutcomesAnalyticsResponse payload = new QuestionOutcomesAnalyticsResponse(
                from,
                to,
                10L,
                List.of(new OutcomeCount(GenerationOutcome.ANSWERED, 8L)),
                0.82,
                new ConfidenceBuckets(7L, 2L, 1L),
                1200.0,
                2500.0,
                List.of(),
                List.of(),
                List.of(new ChannelCount(ChatChannel.SLACK, 10L)),
                List.of()
        );
        when(dashboardService.getQuestionOutcomeAnalytics(from, to, ChatChannel.SLACK)).thenReturn(payload);
        when(dashboardService.getPromptOutcomeAnalytics(promptId, from, to, ChatChannel.TEAMS)).thenReturn(payload);

        String analyticsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "analyst@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.ANALYTICS)
        ));
        String promptsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "prompt@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.PROMPTS)
        ));

        // ANALYTICS authority can read global analytics with channel=SLACK
        mockMvc.perform(get("/api/v1/dash/analytics/outcomes")
                        .header("Authorization", "Bearer " + analyticsJwt)
                        .param("from", "2026-11-18T00:00:00Z")
                        .param("to", "2026-11-21T00:00:00Z")
                        .param("channel", "SLACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_questions").value(10))
                .andExpect(jsonPath("$.outcome_counts[0].outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.channel_counts[0].channel").value("SLACK"))
                .andExpect(jsonPath("$.p95_latency_ms").value(2500.0));

        // PROMPTS-only operator cannot read global analytics
        mockMvc.perform(get("/api/v1/dash/analytics/outcomes")
                        .header("Authorization", "Bearer " + promptsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        // PROMPTS authority can read prompt-scoped analytics
        mockMvc.perform(get("/api/v1/dash/analytics/prompts/{prompt_version_id}/outcomes", promptId)
                        .header("Authorization", "Bearer " + promptsJwt)
                        .param("from", "2026-11-18T00:00:00Z")
                        .param("to", "2026-11-21T00:00:00Z")
                        .param("channel", "TEAMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_questions").value(10));

        // ANALYTICS-only operator cannot read prompt-scoped analytics
        mockMvc.perform(get("/api/v1/dash/analytics/prompts/{prompt_version_id}/outcomes", promptId)
                        .header("Authorization", "Bearer " + analyticsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        // Invalid channel returns 400 BAD_REQUEST
        mockMvc.perform(get("/api/v1/dash/analytics/outcomes")
                        .header("Authorization", "Bearer " + analyticsJwt)
                        .param("channel", "discord"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));
    }

    @Test
    void internalIntrospectReturnsActiveForValidActiveOperatorAndInactiveOtherwise() throws Exception {
        UUID operatorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        String validJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "oleh.pona@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.PROMPTS, DashboardOperatorAuthority.ANALYTICS)
        ));
        when(operatorService.findByEmail("oleh.pona@company.com")).thenReturn(Optional.of(new OperatorItem(
                operatorId,
                "oleh.pona@company.com",
                List.of(DashboardOperatorAuthority.PROMPTS, DashboardOperatorAuthority.ANALYTICS),
                DashboardOperatorStatus.ACTIVE,
                now, now, now
        )));

        mockMvc.perform(post("/api/v1/internal/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s"}
                                """.formatted(validJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.operator_id").value(operatorId.toString()))
                .andExpect(jsonPath("$.email").value("oleh.pona@company.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.authorities[0]").value("PROMPTS"));

        mockMvc.perform(post("/api/v1/internal/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "invalid.jwt.token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.operator_id").isEmpty())
                .andExpect(jsonPath("$.authorities").isEmpty());
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

    @Test
    void actuatorHealthEndpointsArePermittedWithoutAuthentication() throws Exception {
        int healthStatus = mockMvc.perform(get("/actuator/health"))
                .andReturn()
                .getResponse()
                .getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, healthStatus);
        org.junit.jupiter.api.Assertions.assertNotEquals(403, healthStatus);

        int subPathStatus = mockMvc.perform(get("/actuator/health/readiness"))
                .andReturn()
                .getResponse()
                .getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, subPathStatus);
        org.junit.jupiter.api.Assertions.assertNotEquals(403, subPathStatus);
    }

    @Test
    void escalationEndpointsEnforceRbacAndSupportLifecycleTransitions() throws Exception {
        UUID escalationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T10:15:30Z");

        EscalationItem item = new EscalationItem(
                escalationId,
                "req-123",
                "oleh.pona@company.com",
                "How to get internet compensation?",
                EscalationStatus.PENDING,
                null,
                null,
                null,
                null,
                ChatChannel.SLACK,
                QuestionOutcomeReasonCode.LOW_CONFIDENCE,
                0.31,
                now,
                now,
                null
        );
        EscalationDetail detail = new EscalationDetail(
                escalationId,
                "req-123",
                "oleh.pona@company.com",
                "How to get internet compensation?",
                List.of(new Citation(
                        "IT Compensation Guidelines",
                        "https://company.sharepoint.com/docs/it-comp",
                        "doc-456",
                        "123e4567-e89b-12d3-a456-426614174000",
                        0.42
                )),
                EscalationStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                ChatChannel.SLACK,
                QuestionOutcomeReasonCode.LOW_CONFIDENCE,
                0.31,
                now,
                now.plusSeconds(30),
                null,
                List.of(new EscalationStatusHistoryItem(
                        UUID.randomUUID(),
                        null,
                        EscalationStatus.PENDING,
                        null,
                        now
                ))
        );
        EscalationItem updated = new EscalationItem(
                escalationId,
                "req-123",
                "oleh.pona@company.com",
                "How to get internet compensation?",
                EscalationStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                ChatChannel.SLACK,
                QuestionOutcomeReasonCode.LOW_CONFIDENCE,
                0.31,
                now,
                now.plusSeconds(30),
                null
        );

        when(escalationService.getEscalations(eq(0), eq(20), any(), any(), eq(EscalationStatus.PENDING), eq("req-123")))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1L, 1));
        when(escalationService.getEscalationById(escalationId)).thenReturn(detail);
        when(escalationService.updateStatus(escalationId, EscalationStatus.IN_PROGRESS, "expert@company.com"))
                .thenReturn(updated);

        String escalationsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "expert@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.ESCALATIONS)
        ));
        String operatorsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "ops@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.OPERATORS)
        ));
        String logsOnlyJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "logs@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.LOGS)
        ));

        // ESCALATIONS authority can list, view detail, and transition status
        mockMvc.perform(get("/api/v1/dash/escalations")
                        .header("Authorization", "Bearer " + escalationsJwt)
                        .param("status", "PENDING")
                        .param("query_id", "req-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(escalationId.toString()))
                .andExpect(jsonPath("$.items[0].query_id").value("req-123"))
                .andExpect(jsonPath("$.items[0].requester_email").value("oleh.pona@company.com"))
                .andExpect(jsonPath("$.items[0].question_text").value("How to get internet compensation?"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v1/dash/escalations/{id}", escalationId)
                        .header("Authorization", "Bearer " + escalationsJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(escalationId.toString()))
                .andExpect(jsonPath("$.requester_email").value("oleh.pona@company.com"))
                .andExpect(jsonPath("$.question_text").value("How to get internet compensation?"))
                .andExpect(jsonPath("$.source_references[0].document_id").value("doc-456"))
                .andExpect(jsonPath("$.status_history[0].to_status").value("PENDING"));

        mockMvc.perform(patch("/api/v1/dash/escalations/{id}/status", escalationId)
                        .header("Authorization", "Bearer " + escalationsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // OPERATORS authority also has access for backward compatibility
        mockMvc.perform(get("/api/v1/dash/escalations/{id}", escalationId)
                        .header("Authorization", "Bearer " + operatorsJwt))
                .andExpect(status().isOk());

        // LOGS-only operator is forbidden
        mockMvc.perform(get("/api/v1/dash/escalations")
                        .header("Authorization", "Bearer " + logsOnlyJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        // Invalid transition returns 400 BAD_REQUEST
        when(escalationService.updateStatus(escalationId, EscalationStatus.CLOSED, "expert@company.com"))
                .thenThrow(new IllegalArgumentException("Invalid escalation status transition from PENDING to CLOSED."));

        mockMvc.perform(patch("/api/v1/dash/escalations/{id}/status", escalationId)
                        .header("Authorization", "Bearer " + escalationsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CLOSED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));

        UUID operatorId = UUID.randomUUID();
        UUID resolutionId = UUID.randomUUID();
        EscalationDetail answeredDetail = new EscalationDetail(
                escalationId,
                "req-123",
                "oleh.pona@company.com",
                "How to get internet compensation?",
                List.of(),
                EscalationStatus.RESOLVED,
                resolutionId,
                "Submit the IT reimbursement form in the Finance portal.",
                now.plusSeconds(60),
                operatorId,
                ChatChannel.SLACK,
                QuestionOutcomeReasonCode.LOW_CONFIDENCE,
                0.31,
                now,
                now.plusSeconds(60),
                now.plusSeconds(60),
                List.of(new EscalationStatusHistoryItem(
                        UUID.randomUUID(),
                        EscalationStatus.IN_PROGRESS,
                        EscalationStatus.RESOLVED,
                        operatorId,
                        now.plusSeconds(60)
                ))
        );
        when(escalationService.saveResponse(
                escalationId,
                "Submit the IT reimbursement form in the Finance portal.",
                EscalationStatus.RESOLVED,
                "expert@company.com"
        )).thenReturn(answeredDetail);

        mockMvc.perform(put("/api/v1/dash/escalations/{id}/response", escalationId)
                        .header("Authorization", "Bearer " + escalationsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolution_text": "Submit the IT reimbursement form in the Finance portal.",
                                  "status": "RESOLVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution_text").value("Submit the IT reimbursement form in the Finance portal."))
                .andExpect(jsonPath("$.resolved_by_operator_id").value(operatorId.toString()))
                .andExpect(jsonPath("$.resolution_id").value(resolutionId.toString()));

        mockMvc.perform(put("/api/v1/dash/escalations/{id}/response", escalationId)
                        .header("Authorization", "Bearer " + logsOnlyJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolution_text": "Unauthorized"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/dash/escalations/{id}/response", escalationId)
                        .header("Authorization", "Bearer " + escalationsJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolution_text": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));
    }

    @Test
    void escalationAnalyticsEnforcesPermissionBoundaries() throws Exception {
        Instant from = Instant.parse("2026-11-18T00:00:00Z");
        Instant to = Instant.parse("2026-11-21T00:00:00Z");

        EscalationAnalyticsResponse payload = new EscalationAnalyticsResponse(
                from,
                to,
                10L,
                List.of(
                        new EscalationStatusCount(EscalationStatus.PENDING, 4L),
                        new EscalationStatusCount(EscalationStatus.RESOLVED, 6L)
                ),
                6L,
                0.6,
                5400000.0,
                14400000.0,
                List.of(new DailyEscalationPoint(LocalDate.of(2026, 11, 18), 4L, 2L))
        );
        when(escalationService.getAnalytics(from, to, ChatChannel.SLACK)).thenReturn(payload);

        String analyticsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "analyst@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.ANALYTICS)
        ));
        String escalationsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "expert@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.ESCALATIONS)
        ));
        String promptsJwt = jwtService.generateAccessToken(new AccessTokenPayload(
                "prompt@company.com",
                DashboardOperatorStatus.ACTIVE,
                List.of(DashboardOperatorAuthority.PROMPTS)
        ));

        // ANALYTICS authority receives aggregated metrics without any sensitive content
        mockMvc.perform(get("/api/v1/dash/analytics/escalations")
                        .header("Authorization", "Bearer " + analyticsJwt)
                        .param("from", "2026-11-18T00:00:00Z")
                        .param("to", "2026-11-21T00:00:00Z")
                        .param("channel", "slack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_escalations").value(10))
                .andExpect(jsonPath("$.status_counts[0].status").value("PENDING"))
                .andExpect(jsonPath("$.resolved_count").value(6))
                .andExpect(jsonPath("$.resolution_rate").value(0.6))
                .andExpect(jsonPath("$.avg_resolution_time_ms").value(5400000.0))
                .andExpect(jsonPath("$.p95_resolution_time_ms").value(14400000.0))
                .andExpect(jsonPath("$.daily_trend[0].date").value("2026-11-18"))
                .andExpect(jsonPath("$.question_text").doesNotExist())
                .andExpect(jsonPath("$.resolution_text").doesNotExist())
                .andExpect(jsonPath("$.requester_email").doesNotExist())
                .andExpect(jsonPath("$.source_references").doesNotExist())
                .andExpect(jsonPath("$.resolved_by_operator_id").doesNotExist());

        // Permission boundary: ANALYTICS-only operator cannot read raw escalations or details
        mockMvc.perform(get("/api/v1/dash/escalations")
                        .header("Authorization", "Bearer " + analyticsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/dash/escalations/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + analyticsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        // ESCALATIONS-only and PROMPTS-only operators cannot access global escalation analytics
        mockMvc.perform(get("/api/v1/dash/analytics/escalations")
                        .header("Authorization", "Bearer " + escalationsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/dash/analytics/escalations")
                        .header("Authorization", "Bearer " + promptsJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));

        // Invalid channel returns 400 BAD_REQUEST
        mockMvc.perform(get("/api/v1/dash/analytics/escalations")
                        .header("Authorization", "Bearer " + analyticsJwt)
                        .param("channel", "discord"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));

        // Unauthenticated request returns 401 UNAUTHORIZED
        mockMvc.perform(get("/api/v1/dash/analytics/escalations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        // Invalid time range (from > to) returns 400 BAD_REQUEST
        when(escalationService.getAnalytics(to, from, null))
                .thenThrow(new IllegalArgumentException("Query parameter 'from' must not be after 'to'."));
        mockMvc.perform(get("/api/v1/dash/analytics/escalations")
                        .header("Authorization", "Bearer " + analyticsJwt)
                        .param("from", "2026-11-21T00:00:00Z")
                        .param("to", "2026-11-18T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));
    }
}
