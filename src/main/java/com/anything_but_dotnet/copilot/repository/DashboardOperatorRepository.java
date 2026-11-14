package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DashboardOperatorRepository extends JpaRepository<DashboardOperator, UUID> {
    Optional<DashboardOperator> findByEmail(String email);
}
