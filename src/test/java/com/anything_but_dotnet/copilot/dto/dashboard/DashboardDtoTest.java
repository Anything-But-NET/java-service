package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ChannelCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ConfidenceBuckets;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.DailyOutcomePoint;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ErrorCategoryCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.OutcomeCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.ReasonCodeCount;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.LoginRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.RefreshTokenRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.TokenIntrospectResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.TokensResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.ErrorResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.GenerationLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.DocumentMetadataItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.OperatorItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UpdateOperatorStatusRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UserAclItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.DailyEscalationPoint;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.EscalationAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.EscalationStatusCount;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.DashboardErrorCode;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldSerializeAndDeserializeAuthDtosWithSnakeCase() throws Exception {
        LoginRequest login = objectMapper.readValue(
                """
                {"email": "oleh.pona@company.com", "password": "secret"}
                """,
                LoginRequest.class
        );
        assertEquals("oleh.pona@company.com", login.email());
        assertTrue(validator.validate(login).isEmpty());

        RefreshTokenRequest refresh = objectMapper.readValue(
                """
                {"refresh_token": "refresh.jwt.value"}
                """,
                RefreshTokenRequest.class
        );
        assertEquals("refresh.jwt.value", refresh.refreshToken());

        TokensResponse tokens = new TokensResponse("access.jwt", 900L, "refresh.jwt", 604800L);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(tokens));
        assertEquals("access.jwt", json.get("access_token").asText());
        assertEquals(900L, json.get("access_expires_in").asLong());
        assertEquals("refresh.jwt", json.get("refresh_token").asText());
        assertEquals(604800L, json.get("refresh_expires_in").asLong());
        assertFalse(json.has("token_type"));
        assertFalse(json.has("expires_in"));

        TokenIntrospectResponse inactive = TokenIntrospectResponse.inactive();
        JsonNode inactiveJson = objectMapper.readTree(objectMapper.writeValueAsString(inactive));
        assertFalse(inactiveJson.get("active").asBoolean());
        assertTrue(inactiveJson.get("operator_id").isNull());
        assertTrue(inactiveJson.get("authorities").isArray());
    }

    @Test
    void shouldRejectInvalidLoginAndConfidenceBounds() {
        assertFalse(validator.validate(new LoginRequest("not-an-email", "")).isEmpty());
        assertFalse(validator.validate(new TokensResponse("a", 0, "b", 604800)).isEmpty());
        assertFalse(validator.validate(new TokensResponse("a", 900, "b", 0)).isEmpty());

        GenerationLogItem invalidScore = new GenerationLogItem(
                UUID.randomUUID(),
                "req-1",
                ChatChannel.SLACK,
                GenerationOutcome.ANSWERED,
                null,
                null,
                1.5,
                100,
                null,
                null,
                null,
                Instant.parse("2026-11-13T12:00:00Z"),
                Instant.parse("2026-11-13T12:00:00Z")
        );
        assertFalse(validator.validate(invalidScore).isEmpty());
    }

    @Test
    void shouldMapLogsAndWrapInGenericPageResponse() throws Exception {
        Instant started = Instant.parse("2026-11-13T12:00:00Z");
        Instant finished = Instant.parse("2026-11-13T12:01:00Z");
        UUID runId = UUID.randomUUID();
        DocumentSyncRun run = new DocumentSyncRun(runId, "doc-456", started);
        run.markCompleted(finished);

        PageImpl<DocumentSyncRun> springPage = new PageImpl<>(
                List.of(run),
                PageRequest.of(0, 20),
                1
        );
        PageResponse<IngestionLogItem> page = PageResponse.from(springPage, IngestionLogItem::from);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(page));
        assertEquals(0, json.get("page").asInt());
        assertEquals(20, json.get("size").asInt());
        assertEquals(1, json.get("total_elements").asLong());
        assertEquals(1, json.get("total_pages").asInt());
        assertEquals("doc-456", json.at("/items/0/document_id").asText());
        assertEquals(SyncStatus.COMPLETED.name(), json.at("/items/0/status").asText());
        assertEquals("2026-11-13T12:00:00Z", json.at("/items/0/started_at").asText());
        assertEquals("2026-11-13T12:01:00Z", json.at("/items/0/finished_at").asText());

        UUID promptId = UUID.randomUUID();
        GenerationDiagnostics failure = GenerationDiagnostics.failure(
                UUID.randomUUID(),
                "req-999",
                ChatChannel.TEAMS,
                ErrorCategory.INTERNAL_CONNECTION_ERROR,
                1234,
                promptId,
                started
        );
        GenerationLogItem genItem = GenerationLogItem.from(failure);
        assertEquals(GenerationOutcome.FAILURE, genItem.outcome());
        assertEquals(ChatChannel.TEAMS, genItem.channel());
        assertEquals(ErrorCategory.INTERNAL_CONNECTION_ERROR, genItem.errorCategory());
        assertEquals(1234, genItem.latencyMs());
        assertEquals(promptId, genItem.promptVersionId());
        assertNull(genItem.confidenceScore());
        assertNull(genItem.reasonCode());
        assertEquals(started, genItem.updatedAt());

        JsonNode genJson = objectMapper.readTree(objectMapper.writeValueAsString(genItem));
        assertEquals("TEAMS", genJson.get("channel").asText());
        assertEquals(1234, genJson.get("latency_ms").asInt());
        assertEquals(promptId.toString(), genJson.get("prompt_version_id").asText());
    }

    @Test
    void shouldSerializeQuestionOutcomesAnalyticsMatchingOpenApiContract() throws Exception {
        QuestionOutcomesAnalyticsResponse response = new QuestionOutcomesAnalyticsResponse(
                Instant.parse("2026-11-18T00:00:00Z"),
                Instant.parse("2026-11-21T00:00:00Z"),
                100L,
                List.of(new OutcomeCount(GenerationOutcome.ANSWERED, 72L)),
                0.78,
                new ConfidenceBuckets(65L, 20L, 12L),
                1420.5,
                3180.0,
                List.of(new ReasonCodeCount(QuestionOutcomeReasonCode.LOW_CONFIDENCE, 12L)),
                List.of(new ErrorCategoryCount(ErrorCategory.RETRIEVAL_TIMEOUT, 2L)),
                List.of(new ChannelCount(ChatChannel.SLACK, 92L)),
                List.of(new DailyOutcomePoint(
                        LocalDate.of(2026, 11, 18),
                        30L,
                        List.of(new OutcomeCount(GenerationOutcome.ANSWERED, 22L))
                ))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertEquals("2026-11-18T00:00:00Z", json.get("from").asText());
        assertEquals("2026-11-21T00:00:00Z", json.get("to").asText());
        assertEquals(100L, json.get("total_questions").asLong());
        assertEquals("ANSWERED", json.at("/outcome_counts/0/outcome").asText());
        assertEquals(72L, json.at("/outcome_counts/0/count").asLong());
        assertEquals(65L, json.at("/confidence_buckets/high").asLong());
        assertEquals(3180.0, json.get("p95_latency_ms").asDouble());
        assertEquals("LOW_CONFIDENCE", json.at("/reason_code_counts/0/reason_code").asText());
        assertEquals("RETRIEVAL_TIMEOUT", json.at("/error_category_counts/0/error_category").asText());
        assertEquals("SLACK", json.at("/channel_counts/0/channel").asText());
        assertEquals("2026-11-18", json.at("/daily_trend/0/date").asText());
        assertEquals(30L, json.at("/daily_trend/0/total_questions").asLong());
    }

    @Test
    void shouldMapDocumentsUsersOperatorsAndErrorResponse() throws Exception {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");

        DocumentMetadata doc = DocumentMetadata.create("doc-1");
        doc.applyUpsert("sharepoint", "Guide", "https://company.sharepoint.com/docs/1", now, now);
        DocumentMetadataItem docItem = DocumentMetadataItem.from(doc);
        assertEquals("doc-1", docItem.documentId());
        assertEquals("sharepoint", docItem.sourceSystem());

        User user = new User("oleh.pona@company.com");
        user.applyUpsert(List.of("devs", "all-employees"), now, now);
        UserAclItem userItem = UserAclItem.from(user);
        assertEquals(List.of("devs", "all-employees"), userItem.allowedGroups());

        OperatorItem operator = new OperatorItem(
                UUID.randomUUID(),
                "admin@company.com",
                List.of(DashboardOperatorAuthority.LOGS, DashboardOperatorAuthority.OPERATORS),
                DashboardOperatorStatus.ACTIVE,
                now,
                now,
                now
        );
        JsonNode opJson = objectMapper.readTree(objectMapper.writeValueAsString(operator));
        assertEquals("ACTIVE", opJson.get("status").asText());
        assertEquals("LOGS", opJson.at("/authorities/0").asText());
        assertFalse(opJson.has("password_hash"));

        UpdateOperatorStatusRequest statusReq = objectMapper.readValue(
                """
                {"status": "BLOCKED"}
                """,
                UpdateOperatorStatusRequest.class
        );
        assertEquals(DashboardOperatorStatus.BLOCKED, statusReq.status());

        ErrorResponse err = ErrorResponse.of(DashboardErrorCode.FORBIDDEN, "Operator is BLOCKED", now);
        JsonNode errJson = objectMapper.readTree(objectMapper.writeValueAsString(err));
        assertEquals("FORBIDDEN", errJson.get("error_code").asText());
        assertEquals("2026-11-13T12:00:00Z", errJson.get("timestamp").asText());
    }

    @Test
    void shouldSerializeEscalationAnalyticsAndResolutionLogMetadataWithoutSensitiveFields() throws Exception {
        EscalationAnalyticsResponse response = new EscalationAnalyticsResponse(
                Instant.parse("2026-11-18T00:00:00Z"),
                Instant.parse("2026-11-21T00:00:00Z"),
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

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertEquals("2026-11-18T00:00:00Z", json.get("from").asText());
        assertEquals("2026-11-21T00:00:00Z", json.get("to").asText());
        assertEquals(10L, json.get("total_escalations").asLong());
        assertEquals("PENDING", json.at("/status_counts/0/status").asText());
        assertEquals(6L, json.get("resolved_count").asLong());
        assertEquals(0.6, json.get("resolution_rate").asDouble());
        assertEquals(5400000.0, json.get("avg_resolution_time_ms").asDouble());
        assertEquals(14400000.0, json.get("p95_resolution_time_ms").asDouble());
        assertEquals("2026-11-18", json.at("/daily_trend/0/date").asText());
        assertEquals(2L, json.at("/daily_trend/0/resolved_count").asLong());
        assertFalse(json.has("question_text"));
        assertFalse(json.has("resolution_text"));
        assertFalse(json.has("requester_email"));
        assertFalse(json.has("source_references"));
        assertFalse(json.has("resolved_by_operator_id"));

        Instant created = Instant.parse("2026-11-23T10:00:00Z");
        Instant resolved = Instant.parse("2026-11-23T11:00:00Z");
        GenerationDiagnostics diag = GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), "req-res-1", ChatChannel.TEAMS, QuestionOutcomeReasonCode.LOW_CONFIDENCE, 0.3, 900, null, created
        );
        diag.markEscalated(created);
        Escalation esc = Escalation.createPending(
                UUID.randomUUID(), "req-res-1", "oleh@company.com", "Confidential question", "[]", created
        );
        esc.recordResponse("Confidential answer", UUID.randomUUID(), EscalationStatus.RESOLVED, resolved);

        JsonNode logJson = objectMapper.readTree(objectMapper.writeValueAsString(GenerationLogItem.from(diag, esc)));
        assertEquals(esc.getId().toString(), logJson.get("escalation_id").asText());
        assertEquals("RESOLVED", logJson.get("resolution_status").asText());
        assertEquals(esc.getResolutionId().toString(), logJson.get("resolution_id").asText());
        assertEquals("2026-11-23T11:00:00Z", logJson.get("resolved_at").asText());
        assertFalse(logJson.has("question_text"));
        assertFalse(logJson.has("resolution_text"));
    }
}
