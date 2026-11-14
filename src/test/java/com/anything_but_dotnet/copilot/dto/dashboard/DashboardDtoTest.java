package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.LoginRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.RefreshTokenRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.TokensResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.ErrorResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.GenerationLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.DocumentMetadataItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.OperatorItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UpdateOperatorStatusRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UserAclItem;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.model.DashboardErrorCode;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
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
    }

    @Test
    void shouldRejectInvalidLoginAndConfidenceBounds() {
        assertFalse(validator.validate(new LoginRequest("not-an-email", "")).isEmpty());
        assertFalse(validator.validate(new TokensResponse("a", 0, "b", 604800)).isEmpty());
        assertFalse(validator.validate(new TokensResponse("a", 900, "b", 0)).isEmpty());

        GenerationLogItem invalidScore = new GenerationLogItem(
                UUID.randomUUID(),
                "req-1",
                GenerationOutcome.SUCCESS,
                null,
                1.5,
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

        GenerationDiagnostics failure = GenerationDiagnostics.failure(
                UUID.randomUUID(),
                "req-999",
                ErrorCategory.INTERNAL_CONNECTION_ERROR,
                started
        );
        GenerationLogItem genItem = GenerationLogItem.from(failure);
        assertEquals(GenerationOutcome.FAILURE, genItem.outcome());
        assertEquals(ErrorCategory.INTERNAL_CONNECTION_ERROR, genItem.errorCategory());
        assertNull(genItem.confidenceScore());
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
}
