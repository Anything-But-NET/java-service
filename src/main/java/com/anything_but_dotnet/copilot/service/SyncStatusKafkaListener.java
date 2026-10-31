package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.KafkaSyncStatusEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class SyncStatusKafkaListener {

    private static final Logger logger = LoggerFactory.getLogger(SyncStatusKafkaListener.class);
    private final DocumentSyncRunRepository repository;
    private final Clock clock;

    public SyncStatusKafkaListener(DocumentSyncRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(topics = "rag.sync.status.v1", groupId = "${spring.kafka.consumer.group-id:core-group}")
    public void handleSyncStatusEvent(KafkaSyncStatusEvent event) {
        logger.info("Received sync status {} for document {}", event.status(), event.documentId());

        UUID syncId = UUID.fromString(event.syncId());
        Instant now = Instant.now(clock);

        DocumentSyncRun run = repository.findById(syncId)
                .orElseGet(() -> new DocumentSyncRun(syncId, event.documentId(), now));

        if (event.status() == SyncStatus.COMPLETED) {
            run.markCompleted(now);
        } else if (event.status() != SyncStatus.IN_PROGRESS) {
            run.markFailed(event.status(), event.errorMessage(), now);
        }

        repository.save(run);
    }
}