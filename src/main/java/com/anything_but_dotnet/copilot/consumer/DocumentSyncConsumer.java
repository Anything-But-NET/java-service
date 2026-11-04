package com.anything_but_dotnet.copilot.consumer;

import com.anything_but_dotnet.copilot.dto.SyncEvent;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import com.anything_but_dotnet.copilot.service.DocumentMetadataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DocumentSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentSyncConsumer.class);

    private final DocumentMetadataService metadataService;
    private final ObjectMapper objectMapper;
    private final DocumentSyncRunRepository syncRunRepository;
    private final Clock clock;

    public DocumentSyncConsumer(DocumentMetadataService metadataService,
                                ObjectMapper objectMapper,
                                DocumentSyncRunRepository syncRunRepository,
                                Clock clock) {
        this.metadataService = metadataService;
        this.objectMapper = objectMapper;
        this.syncRunRepository = syncRunRepository;
        this.clock = clock;
    }

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.ingestion:rag.documents.ingestion.v1}")
    public void consume(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            log.warn("Received null record (key={} topic={}, partition={}, offset={})",
                    record.key(), record.topic(), record.partition(), record.offset());
            return;
        }

        SyncEvent event = parseEvent(record);

        UUID syncId = event.syncId();
        Instant now = Instant.now(clock);

        syncRunRepository.insertIfNotExists(syncId, event.documentId(), now);
        log.info("Registered IN_PROGRESS sync run {} for document {}", syncId, event.documentId());

        metadataService.processEvent(event);
    }

    private SyncEvent parseEvent(ConsumerRecord<String, String> record) {
        try {
            SyncEvent event = objectMapper.readValue(record.value(), SyncEvent.class);

            if (event == null || event.documentId() == null || event.documentId().isBlank()) {
                log.warn(
                        "Skipping ingestion event with missing document_id (topic={}, partition={}, offset={})",
                        record.topic(), record.partition(), record.offset()
                );
                throw new IllegalArgumentException("Broken ingestion event");
            }
            return event;
        } catch (JsonProcessingException e) {
            log.error(
                    "Invalid ingestion payload (topic={}, partition={}, offset={}): {}",
                    record.topic(), record.partition(), record.offset(), e.getMessage()
            );
            throw new IllegalArgumentException("Incorrect ingestion payload", e);
        }
    }
}