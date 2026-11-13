package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto;
import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.exception.JwtException;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.repository.DashboardOperatorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardOperatorService {
    private final DashboardOperatorRepository dashboardOperatorRepository;
    private final Clock clock;

    public DashboardOperatorService(Clock clock, DashboardOperatorRepository dashboardOperatorRepository) {
        this.clock = clock;
        this.dashboardOperatorRepository = dashboardOperatorRepository;
    }

    public record OperatorItemWithPassword(DashboardResourceDto.OperatorItem operatorItem, String passwordHash) {}

    public OperatorItemWithPassword getOperator(String email) {
        DashboardOperator operator = dashboardOperatorRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Dashboard operator with email " + email + " not found"));
        return new OperatorItemWithPassword(DashboardResourceDto.OperatorItem.from(operator), operator.getPasswordHash());
    }

    public Page<DashboardResourceDto.OperatorItem> getAll(Pageable pageable) {
        return dashboardOperatorRepository.findAll(pageable).map(DashboardResourceDto.OperatorItem::from);
    }

    /**
     * Validates that the operator still exists and is ACTIVE, updates {@code last_refreshed_at},
     * and returns the current operator view so newly issued tokens reflect up-to-date DB claims.
     */
    @Transactional
    public DashboardResourceDto.OperatorItem refreshSession(String email) {
        DashboardOperator operator = dashboardOperatorRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException(JwtException.JwtErrorKind.BAD_JWT, "Operator not found for token"));

        if (operator.getStatus() == DashboardOperatorStatus.BLOCKED) {
            throw new LockedException("Operator account is BLOCKED.");
        }

        operator.setLastRefreshedAt(clock.instant());
        return DashboardResourceDto.OperatorItem.from(dashboardOperatorRepository.save(operator));
    }

    @Transactional
    public void updateLastRefreshed(String email) {
        refreshSession(email);
    }

    @Transactional
    public DashboardResourceDto.OperatorItem updateStatus(UUID id, DashboardOperatorStatus status) {
        DashboardOperator operator = dashboardOperatorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dashboard operator with id " + id + " not found"));
        operator.setStatus(status);
        return DashboardResourceDto.OperatorItem.from(dashboardOperatorRepository.save(operator));
    }
}
