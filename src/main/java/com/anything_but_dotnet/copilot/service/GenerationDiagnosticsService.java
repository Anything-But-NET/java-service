package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class GenerationDiagnosticsService {

    private final GenerationDiagnosticsRepository repository;
    private final Clock clock;

    public GenerationDiagnosticsService(GenerationDiagnosticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void recordSuccess(String queryId, Double confidenceScore) {
        GenerationDiagnostics diagnostics = GenerationDiagnostics.success(
                UUID.randomUUID(), queryId, confidenceScore, Instant.now(clock)
        );
        repository.save(diagnostics);
    }

    public void recordFailure(String queryId, ErrorCategory errorCategory) {
        GenerationDiagnostics diagnostics = GenerationDiagnostics.failure(
                UUID.randomUUID(), queryId, errorCategory, Instant.now(clock)
        );
        repository.save(diagnostics);
    }
}