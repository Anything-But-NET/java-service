package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.SyncEvent;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.repository.DocumentMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class DocumentMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataService.class);

    private final DocumentMetadataRepository repository;
    private final Clock clock;

    public DocumentMetadataService(DocumentMetadataRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void deleteByDocumentId(String documentId) {
        repository.deleteById(documentId);
        log.info("Deleted metadata for document_id={}", documentId);
    }

    @Transactional
    public void processEvent(SyncEvent event) {
        switch (event) {
            case SyncEvent.Upsert upsert -> upsertDocument(upsert);
            case SyncEvent.UpdateAcl updateAcl -> updateAcl(updateAcl);
            case SyncEvent.Delete(_, var documentId) -> deleteByDocumentId(documentId);
        }
    }

    private void upsertDocument(SyncEvent.Upsert event) {
        DocumentMetadata doc = findOrCreate(event.documentId());

        if (doc.isStale(event.lastUpdated())) {
            log.warn(
                    "Skipping stale '{}' event for document_id={} (event last_updated={}, db last_updated={})",
                    event.action(), event.documentId(), event.lastUpdated(), doc.getLastUpdated()
            );
            return;
        }

        doc.applyUpsert(
                event.sourceSystem(),
                event.title(),
                event.url(),
                event.lastUpdated(),
                event.allowedGroups(),
                clock.instant()
        );

        repository.save(doc);
        log.info("Synced document metadata for document_id={} (action={})", event.documentId(), event.action());
    }

    private void updateAcl(SyncEvent.UpdateAcl event) {
        DocumentMetadata doc = findOrCreate(event.documentId());
        doc.applyAcl(event.allowedGroups(), clock.instant());

        repository.save(doc);
        log.info("Updated allowed_groups for document_id={}", event.documentId());
    }

    private DocumentMetadata findOrCreate(String documentId) {
        return repository.findById(documentId)
                .orElseGet(() -> DocumentMetadata.create(documentId));
    }
}
