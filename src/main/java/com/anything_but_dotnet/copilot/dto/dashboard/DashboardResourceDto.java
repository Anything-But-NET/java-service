package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.entity.DocumentMetadata;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resource DTOs for documents, user ACLs, and dashboard operators.
 */
public interface DashboardResourceDto {

    record DocumentMetadataItem(
            @NotBlank
            @JsonProperty("document_id")
            String documentId,

            @JsonProperty("source_system")
            String sourceSystem,

            @JsonProperty("title")
            String title,

            @JsonProperty("url")
            String url,

            @JsonProperty("last_updated")
            Instant lastUpdated,

            @NotNull
            @JsonProperty("last_sync_at")
            Instant lastSyncAt
    ) {
        public static DocumentMetadataItem from(DocumentMetadata doc) {
            return new DocumentMetadataItem(
                    doc.getDocumentId(),
                    doc.getSourceSystem(),
                    doc.getTitle(),
                    doc.getUrl(),
                    doc.getLastUpdated(),
                    doc.getLastSyncAt()
            );
        }
    }

    record UserAclItem(
            @NotBlank
            @Email
            @JsonProperty("email")
            String email,

            @NotNull
            @JsonProperty("allowed_groups")
            List<String> allowedGroups,

            @JsonProperty("last_updated")
            Instant lastUpdated,

            @NotNull
            @JsonProperty("last_sync_at")
            Instant lastSyncAt
    ) {
        public UserAclItem {
            allowedGroups = allowedGroups != null ? List.copyOf(allowedGroups) : List.of();
        }

        public static UserAclItem from(User user) {
            return new UserAclItem(
                    user.getEmail(),
                    user.getAllowedGroups(),
                    user.getLastUpdated(),
                    user.getLastSyncAt()
            );
        }
    }

    record OperatorItem(
            @NotNull
            @JsonProperty("id")
            UUID id,

            @NotBlank
            @Email
            @JsonProperty("email")
            String email,

            @NotNull
            @JsonProperty("authorities")
            List<DashboardOperatorAuthority> authorities,

            @NotNull
            @JsonProperty("status")
            DashboardOperatorStatus status,

            @NotNull
            @JsonProperty("last_refreshed_at")
            Instant lastRefreshedAt,

            @NotNull
            @JsonProperty("last_updated_at")
            Instant lastUpdatedAt,

            @NotNull
            @JsonProperty("created_at")
            Instant createdAt
    ) {
        public OperatorItem {
            authorities = authorities != null ? List.copyOf(authorities) : List.of();
        }

        public static OperatorItem from(DashboardOperator operator) {
            return new OperatorItem(
                    operator.getId(),
                    operator.getEmail(),
                    operator.getAuthorities(),
                    operator.getStatus(),
                    operator.getLastRefreshedAt(),
                    operator.getLastUpdated(),
                    operator.getCreatedAt()
            );
        }
    }

    record UpdateOperatorStatusRequest(
            @NotNull
            @JsonProperty("status")
            DashboardOperatorStatus status
    ) {
    }
}
