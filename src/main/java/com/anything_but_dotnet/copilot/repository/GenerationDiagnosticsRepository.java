package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface GenerationDiagnosticsRepository extends JpaRepository<GenerationDiagnostics, UUID> {}