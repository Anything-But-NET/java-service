package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EscalationStatusHistoryRepository extends JpaRepository<EscalationStatusHistory, UUID> {
    List<EscalationStatusHistory> findByEscalationIdOrderByChangedAtAsc(UUID escalationId);
}