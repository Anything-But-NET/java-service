package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class GenerationDiagnosticsService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationDiagnosticsService.class);

    private final GenerationDiagnosticsRepository repository;
    private final Clock clock;

    public GenerationDiagnosticsService(GenerationDiagnosticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<GenerationDiagnostics> findByQueryId(String queryId) {
        return repository.findFirstByQueryId(queryId);
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            return false;
        }
        return repository.existsByQueryId(queryId);
    }

    @Transactional
    public boolean recordSuccess(String queryId, Double confidenceScore) {
        if (queryId != null && repository.existsByQueryId(queryId)) {
            logger.warn("Diagnostic record already exists for queryId: {}. Skipping duplicate success record.", queryId);
            return false;
        }
        GenerationDiagnostics diagnostics = GenerationDiagnostics.success(
                UUID.randomUUID(), queryId, confidenceScore, Instant.now(clock)
        );
        repository.save(diagnostics);
        return true;
    }

    @Transactional
    public boolean recordFailure(String queryId, ErrorCategory errorCategory) {
        if (queryId != null && repository.existsByQueryId(queryId)) {
            logger.warn("Diagnostic record already exists for queryId: {}. Preventing duplicate escalation/failure record.", queryId);
            return false;
        }
        GenerationDiagnostics diagnostics = GenerationDiagnostics.failure(
                UUID.randomUUID(), queryId, errorCategory, Instant.now(clock)
        );
        repository.save(diagnostics);
        return true;
    }
}