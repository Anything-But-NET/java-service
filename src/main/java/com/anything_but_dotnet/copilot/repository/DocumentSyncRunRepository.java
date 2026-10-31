package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentSyncRunRepository extends JpaRepository<DocumentSyncRun, UUID> {
}