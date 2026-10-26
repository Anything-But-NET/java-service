package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.repository.DocumentMetadataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentSyncConsumer {

    private final DocumentMetadataRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentSyncConsumer(DocumentMetadataRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "new-file-data", groupId = "java-metadata-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            if (record.value() == null) {
                if (record.key() != null) {
                    String docId = record.key().replace("\"", "");
                    repository.deleteById(docId);
                }
                ack.acknowledge();
                return;
            }

            JsonNode rootNode = objectMapper.readTree(record.value());
            JsonNode payload = rootNode.has("message") ? rootNode.get("message") : rootNode;
            
            if (!payload.has("document_id")) {
                ack.acknowledge();
                return;
            }

            String docId = payload.get("document_id").asText();
            
            if (payload.path("is_deleted").asBoolean(false)) {
                repository.deleteById(docId);
                ack.acknowledge();
                return;
            }

            List<String> roles = new ArrayList<>();
            if (payload.has("acl") && payload.get("acl").has("allowed_groups")) {
                payload.get("acl").get("allowed_groups").forEach(node -> roles.add(node.asText()));
            }

            DocumentMetadata doc = repository.findById(docId).orElse(new DocumentMetadata());
            doc.setDocId(docId);
            doc.setAllowedRoles(roles);
            doc.setLastSyncAt(Instant.now());
            repository.save(doc);

            ack.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}