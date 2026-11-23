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
@Table(schema = "core", name = "escalation_status_history")
public class EscalationStatusHistory {

    @Id
    private UUID id;

    @Column(name = "escalation_id", nullable = false, updatable = false)
    private UUID escalationId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "from_status")
    private EscalationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "to_status", nullable = false)
    private EscalationStatus toStatus;

    @Column(name = "changed_by_operator_id")
    private UUID changedByOperatorId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected EscalationStatusHistory() {}

    public EscalationStatusHistory(
            UUID id,
            UUID escalationId,
            EscalationStatus fromStatus,
            EscalationStatus toStatus,
            UUID changedByOperatorId,
            Instant changedAt
    ) {
        this.id = id;
        this.escalationId = escalationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByOperatorId = changedByOperatorId;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getEscalationId() { return escalationId; }
    public EscalationStatus getFromStatus() { return fromStatus; }
    public EscalationStatus getToStatus() { return toStatus; }
    public UUID getChangedByOperatorId() { return changedByOperatorId; }
    public Instant getChangedAt() { return changedAt; }
}