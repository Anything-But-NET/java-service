package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.EscalationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "escalations")
public class Escalation {

    @Id
    private UUID id;

    @Column(name = "query_id", nullable = false, unique = true)
    private String queryId;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "source_references", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String sourceReferences;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private EscalationStatus status;

    @Column(name = "resolution_text", columnDefinition = "text")
    private String resolutionText;

    @Column(name = "resolution_updated_at")
    private Instant resolutionUpdatedAt;

    @Column(name = "resolved_by_operator_id")
    private UUID resolvedByOperatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Escalation() {}

    private Escalation(
            UUID id,
            String queryId,
            String requesterEmail,
            String questionText,
            String sourceReferences,
            EscalationStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.queryId = queryId;
        this.requesterEmail = requesterEmail;
        this.questionText = questionText;
        this.sourceReferences = (sourceReferences == null || sourceReferences.isBlank()) ? "[]" : sourceReferences;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Escalation createPending(
            UUID id,
            String queryId,
            String requesterEmail,
            String questionText,
            String sourceReferences,
            Instant createdAt
    ) {
        return new Escalation(
                id,
                queryId,
                requesterEmail,
                questionText,
                sourceReferences,
                EscalationStatus.PENDING,
                createdAt
        );
    }

    public void transitionTo(EscalationStatus targetStatus, Instant now) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new IllegalArgumentException(
                    "Invalid escalation status transition from " + this.status + " to " + targetStatus + "."
            );
        }
        this.status = targetStatus;
        this.updatedAt = now;
        if (targetStatus.marksResolved() && this.resolvedAt == null) {
            this.resolvedAt = now;
        }
    }

    public void recordResponse(
            String resolutionText,
            UUID operatorId,
            EscalationStatus targetStatus,
            Instant now
    ) {
        if (this.status == EscalationStatus.CLOSED || this.status == EscalationStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Cannot record a response for a " + this.status + " escalation."
            );
        }
        if (resolutionText == null || resolutionText.isBlank()) {
            throw new IllegalArgumentException("resolution_text must not be blank.");
        }
        this.resolutionText = resolutionText.trim();
        this.resolutionUpdatedAt = now;
        this.resolvedByOperatorId = operatorId;
        this.updatedAt = now;
        if (targetStatus != null && targetStatus != this.status) {
            transitionTo(targetStatus, now);
        }
    }

    public UUID getId() { return id; }
    public String getQueryId() { return queryId; }
    public String getRequesterEmail() { return requesterEmail; }
    public String getQuestionText() { return questionText; }
    public String getSourceReferences() { return sourceReferences; }
    public EscalationStatus getStatus() { return status; }
    public String getResolutionText() { return resolutionText; }
    public Instant getResolutionUpdatedAt() { return resolutionUpdatedAt; }
    public UUID getResolvedByOperatorId() { return resolvedByOperatorId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}