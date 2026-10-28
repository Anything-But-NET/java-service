package com.anything_but_dotnet.copilot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "action",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SyncEvent.Upsert.class, name = "create"),
        @JsonSubTypes.Type(value = SyncEvent.Upsert.class, name = "update"),
        @JsonSubTypes.Type(value = SyncEvent.UpdateAcl.class, name = "update_acl"),
        @JsonSubTypes.Type(value = SyncEvent.Delete.class, name = "delete")
})
public sealed interface SyncEvent permits SyncEvent.Upsert, SyncEvent.UpdateAcl, SyncEvent.Delete {

    String documentId();
    String syncId();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Upsert(
            @JsonProperty("sync_id") String syncId,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("action") String action,
            @JsonProperty("source_system") String sourceSystem,
            @JsonProperty("title") String title,
            @JsonProperty("url") String url,
            @JsonProperty("raw_content") String rawContent,
            @JsonProperty("last_updated") Instant lastUpdated,
            @JsonProperty("acl") AclPayload acl
    ) implements SyncEvent {
        public List<String> allowedGroups() {
            return acl != null ? acl.normalizedGroups() : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpdateAcl(
            @JsonProperty("sync_id") String syncId,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("acl") AclPayload acl
    ) implements SyncEvent {
        public List<String> allowedGroups() {
            return acl != null ? acl.normalizedGroups() : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delete(
            @JsonProperty("sync_id") String syncId,
            @JsonProperty("document_id") String documentId
    ) implements SyncEvent {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AclPayload(
            @JsonProperty("allowed_groups") List<String> allowedGroups
    ) {
        public List<String> normalizedGroups() {
            if (allowedGroups == null || allowedGroups.isEmpty()) {
                return List.of();
            }
            return allowedGroups.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }
    }
}
