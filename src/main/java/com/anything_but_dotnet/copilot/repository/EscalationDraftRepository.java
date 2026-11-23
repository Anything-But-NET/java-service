package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.EscalationDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EscalationDraftRepository extends JpaRepository<EscalationDraft, String> {
    Optional<EscalationDraft> findByQueryIdAndExpiresAtAfter(String queryId, Instant now);

    @Modifying
    @Query("DELETE FROM EscalationDraft d WHERE d.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}