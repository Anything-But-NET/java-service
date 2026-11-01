package com.anything_but_dotnet.copilot.consumer;

import com.anything_but_dotnet.copilot.dto.KafkaSyncStatusEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class SyncStatusConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SyncStatusConsumer.class);
    private final DocumentSyncRunRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SyncStatusConsumer(DocumentSyncRunRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.sync-status:rag.sync.status.v1}",
            groupId = "${app.kafka.consumer.sync-status-group:sync-status-group}"
    )
    public void handleSyncStatusEvent(String payload) {
        try {
            KafkaSyncStatusEvent event = objectMapper.readValue(payload, KafkaSyncStatusEvent.class);

            if (event.syncId() == null || event.syncId().isBlank()) {
                throw new IllegalArgumentException("sync_id cannot be null or empty");
            }

            UUID syncId = UUID.fromString(event.syncId());
            Instant now = Instant.now(clock);

            logger.info("Received sync status {} for document {}", event.status(), event.documentId());

            DocumentSyncRun run = repository.findById(syncId)
                    .orElseGet(() -> new DocumentSyncRun(syncId, event.documentId(), now));

            if (event.status() == SyncStatus.COMPLETED) {
                run.markCompleted(now);
            } else if (event.status() != SyncStatus.IN_PROGRESS) {
                run.markFailed(event.status(), event.errorMessage(), now);
            }

            repository.save(run);

        } catch (Exception e) {
            logger.error("Failed to process sync status event. Payload: {}", payload, e);
            throw new RuntimeException("Failed to process Kafka event", e);
        }
    }
}
