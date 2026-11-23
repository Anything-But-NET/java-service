package com.anything_but_dotnet.copilot.model;

/**
 * Lifecycle status of a persisted support escalation (matches {@code core.escalation_status}).
 */
public enum EscalationStatus {
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public boolean canTransitionTo(EscalationStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == IN_PROGRESS || target == RESOLVED || target == CANCELLED;
            case IN_PROGRESS -> target == RESOLVED || target == CLOSED || target == CANCELLED;
            case RESOLVED -> target == CLOSED;
            case CLOSED, CANCELLED -> false;
        };
    }

    public boolean marksResolved() {
        return this == RESOLVED || this == CLOSED;
    }
}