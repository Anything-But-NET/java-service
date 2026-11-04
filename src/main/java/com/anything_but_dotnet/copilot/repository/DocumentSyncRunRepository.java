package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface DocumentSyncRunRepository extends JpaRepository<DocumentSyncRun, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO core.document_sync_runs (id, document_id, status, started_at)
                    VALUES (:id, :documentId, CAST('IN_PROGRESS' AS core.sync_status), :startedAt)
                    ON CONFLICT (id) DO NOTHING
                    """,
            nativeQuery = true
    )
    void insertIfNotExists(@Param("id") UUID id,
                           @Param("documentId") String documentId,
                           @Param("startedAt") Instant startedAt);
}