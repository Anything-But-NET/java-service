package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenerationDiagnosticsService {
    private static final Logger log = LoggerFactory.getLogger(GenerationDiagnosticsService.class);
    private final GenerationDiagnosticsRepository repository;

    public GenerationDiagnosticsService(GenerationDiagnosticsRepository repository) {
        this.repository = repository;
    }

    public void recordSuccess(String queryId, Double confidenceScore) {
        log.info("Recording safe diagnostics: success for query {}", queryId);
        repository.save(new GenerationDiagnostics(queryId, "success", null, confidenceScore));
    }

    public void recordFailure(String queryId, String errorCategory) {
        log.error("Recording safe diagnostics: failure for query {} with category {}", queryId, errorCategory);
        repository.save(new GenerationDiagnostics(queryId, "failure", errorCategory, null));
    }
}