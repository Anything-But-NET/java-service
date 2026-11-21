package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GenerationDiagnosticsService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationDiagnosticsService.class);

    private final GenerationDiagnosticsRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GenerationDiagnosticsService(
            GenerationDiagnosticsRepository repository,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public Optional<GenerationDiagnostics> findByQueryId(String queryId) {
        return repository.findFirstByQueryId(queryId);
    }

    public boolean isAlreadyProcessed(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            return false;
        }
        return repository.existsByQueryId(queryId);
    }

    @Transactional
    public boolean recordAnswered(
            String queryId,
            ChatChannel channel,
            Double confidenceScore,
            Integer latencyMs,
            UUID promptVersionId
    ) {
        return saveIfAbsent(GenerationDiagnostics.answered(
                UUID.randomUUID(), queryId, channel, confidenceScore, latencyMs, promptVersionId, Instant.now(clock)
        ));
    }

    @Transactional
    public boolean recordSafeNoAnswer(
            String queryId,
            ChatChannel channel,
            QuestionOutcomeReasonCode reasonCode,
            Double confidenceScore,
            Integer latencyMs,
            UUID promptVersionId
    ) {
        return saveIfAbsent(GenerationDiagnostics.safeNoAnswer(
                UUID.randomUUID(), queryId, channel, reasonCode, confidenceScore, latencyMs, promptVersionId, Instant.now(clock)
        ));
    }

    @Transactional
    public boolean recordFailure(
            String queryId,
            ChatChannel channel,
            ErrorCategory errorCategory,
            Integer latencyMs,
            UUID promptVersionId
    ) {
        return saveIfAbsent(GenerationDiagnostics.failure(
                UUID.randomUUID(), queryId, channel, errorCategory, latencyMs, promptVersionId, Instant.now(clock)
        ));
    }

    @Transactional
    public boolean markEscalated(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            return false;
        }
        Optional<GenerationDiagnostics> existingOpt = repository.findFirstByQueryId(queryId);
        if (existingOpt.isEmpty()) {
            logger.warn("Cannot escalate unknown queryId: {}", queryId);
            return false;
        }
        GenerationDiagnostics existing = existingOpt.get();
        if (existing.getOutcome() == GenerationOutcome.ESCALATED || existing.getOutcome() == GenerationOutcome.FAILURE) {
            return false;
        }
        existing.markEscalated(Instant.now(clock));
        repository.save(existing);
        return true;
    }

    public QuestionOutcomesAnalyticsResponse getAnalytics(
            Instant from,
            Instant to,
            ChatChannel channel,
            UUID promptVersionId
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Query parameter 'from' must not be after 'to'.");
        }
        String channelFilter = channel != null ? channel.name() : null;
        String json = repository.getAnalyticsJson(from, to, channelFilter, promptVersionId);
        if (json == null || json.isBlank()) {
            return QuestionOutcomesAnalyticsResponse.empty(from, to);
        }
        try {
            return objectMapper.readValue(json, QuestionOutcomesAnalyticsResponse.class)
                    .withTimeRange(from, to);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize analytics aggregation JSON.", e);
        }
    }

    private boolean saveIfAbsent(GenerationDiagnostics diagnostics) {
        String queryId = diagnostics.getQueryId();
        if (queryId != null && repository.existsByQueryId(queryId)) {
            logger.warn("Diagnostic record already exists for queryId: {}. Skipping duplicate {} record.",
                    queryId, diagnostics.getOutcome());
            return false;
        }
        repository.save(diagnostics);
        return true;
    }
}
