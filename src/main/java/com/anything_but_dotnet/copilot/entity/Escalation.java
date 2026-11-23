package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.EscalationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private EscalationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Escalation() {}

    private Escalation(UUID id, String queryId, EscalationStatus status, Instant createdAt) {
        this.id = id;
        this.queryId = queryId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Escalation createPending(UUID id, String queryId, Instant createdAt) {
        return new Escalation(id, queryId, EscalationStatus.PENDING, createdAt);
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

    public UUID getId() { return id; }
    public String getQueryId() { return queryId; }
    public EscalationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}