package com.anything_but_dotnet.copilot.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @Column(name = "doc_id", nullable = false, updatable = false)
    private String docId;

    @ElementCollection
    @CollectionTable(name = "document_roles", joinColumns = @JoinColumn(name = "doc_id"))
    @Column(name = "role")
    private List<String> allowedRoles = new ArrayList<>();

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public List<String> getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}