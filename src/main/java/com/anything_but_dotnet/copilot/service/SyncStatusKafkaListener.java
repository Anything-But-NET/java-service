package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.KafkaSyncStatusEvent;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
public class SyncStatusKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(SyncStatusKafkaListener.class);
    private final DocumentSyncRunRepository repository;

    public SyncStatusKafkaListener(DocumentSyncRunRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "rag.sync.status.v1", groupId = "copilot-core-group")
    @Transactional
    public void handleSyncStatusEvent(KafkaSyncStatusEvent event) {
        log.info("Received sync status event for document {}: {}", event.document_id(), event.status());

        // Upsert logic: if exists, update it; otherwise, create a new run
        DocumentSyncRun run = repository.findById(event.sync_id())
                .orElseGet(() -> new DocumentSyncRun(
                        event.sync_id(),
                        event.document_id(),
                        event.status(),
                        event.error_message()
                ));

        run.setStatus(event.status());
        run.setErrorMessage(event.error_message());

        // If the process is terminal, set finished_at
        if (event.status() != com.anything_but_dotnet.copilot.entity.SyncStatus.IN_PROGRESS) {
            run.setFinishedAt(ZonedDateTime.now());
        }

        repository.save(run);
    }
}
