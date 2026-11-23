package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscalationRepository
        extends JpaRepository<Escalation, UUID>, JpaSpecificationExecutor<Escalation> {
    Optional<Escalation> findByQueryId(String queryId);
    List<Escalation> findByQueryIdIn(Collection<String> queryIds);
}