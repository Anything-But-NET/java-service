package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(schema = "core", name = "users")
public class User {
    @Id
    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_groups", columnDefinition = "text[]", nullable = false)
    private List<String> allowedGroups = new ArrayList<>();

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;

    protected User() {}

    public User(String email) {
        this.email = Objects.requireNonNull(email, "email must not be null");
    }

    public void applyUpsert(List<String> allowedGroups, Instant lastUpdated, Instant lastSyncAt) {
        this.allowedGroups = allowedGroups != null ? new ArrayList<>(allowedGroups) : new ArrayList<>();
        this.lastUpdated = lastUpdated;
        this.lastSyncAt = Objects.requireNonNull(lastSyncAt, "lastSyncAt must not be null");
    }

    public boolean isStale(Instant incomingLastUpdated) {
        return this.lastUpdated != null
                && incomingLastUpdated != null
                && incomingLastUpdated.isBefore(this.lastUpdated);
    }

    public String getEmail() {
        return email;
    }

    public void setAllowedGroups(List<String> allowedGroups) {
        this.allowedGroups = allowedGroups != null ? new ArrayList<>(allowedGroups) : new ArrayList<>();
    }

    public List<String> getAllowedGroups() {
        return List.copyOf(allowedGroups);
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }
}
