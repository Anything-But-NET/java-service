package com.anything_but_dotnet.copilot.entity;

import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.*;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "dashboard_operators")
public class DashboardOperator {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "authorities", columnDefinition = "core.dashboard_operator_authority[]", nullable = false)
    @ColumnTransformer(write = "CAST(? AS core.dashboard_operator_authority[])")
    private List<DashboardOperatorAuthority> authorities = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private DashboardOperatorStatus status;

    @UpdateTimestamp
    @Column(name = "last_updated_at")
    private Instant lastUpdated;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public List<DashboardOperatorAuthority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<DashboardOperatorAuthority> authorities) {
        this.authorities = authorities;
    }

    public DashboardOperatorStatus getStatus() {
        return status;
    }

    public void setStatus(DashboardOperatorStatus status) {
        this.status = status;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Instant lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}