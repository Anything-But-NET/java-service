package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(schema = "core", name = "escalation_drafts")
public class EscalationDraft {

    @Id
    @Column(name = "query_id", nullable = false)
    private String queryId;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "source_references", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String sourceReferences;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EscalationDraft() {}

    public EscalationDraft(
            String queryId,
            String requesterEmail,
            String questionText,
            String sourceReferences,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.queryId = queryId;
        this.requesterEmail = requesterEmail;
        this.questionText = questionText;
        this.sourceReferences = (sourceReferences == null || sourceReferences.isBlank()) ? "[]" : sourceReferences;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getQueryId() { return queryId; }
    public String getRequesterEmail() { return requesterEmail; }
    public String getQuestionText() { return questionText; }
    public String getSourceReferences() { return sourceReferences; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}