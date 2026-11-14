package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto;
import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.exception.JwtException;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.repository.DashboardOperatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardOperatorServiceTest {

    @Mock
    private DashboardOperatorRepository repository;

    private final Instant fixedNow = Instant.parse("2026-11-13T18:00:00Z");
    private DashboardOperatorService service;

    @BeforeEach
    void setUp() {
        service = new DashboardOperatorService(Clock.fixed(fixedNow, ZoneOffset.UTC), repository);
    }

    @Test
    void refreshSessionUpdatesTimestampForActiveOperator() {
        DashboardOperator operator = buildOperator(DashboardOperatorStatus.ACTIVE);
        when(repository.findByEmail("admin@company.com")).thenReturn(Optional.of(operator));
        when(repository.save(any(DashboardOperator.class))).thenAnswer(inv -> inv.getArgument(0));

        DashboardResourceDto.OperatorItem result = service.refreshSession("admin@company.com");

        assertEquals(fixedNow, result.lastRefreshedAt());
        assertEquals(DashboardOperatorStatus.ACTIVE, result.status());
    }

    @Test
    void refreshSessionThrowsLockedForBlockedOperator() {
        DashboardOperator blocked = buildOperator(DashboardOperatorStatus.BLOCKED);
        when(repository.findByEmail("admin@company.com")).thenReturn(Optional.of(blocked));

        assertThrows(LockedException.class, () -> service.refreshSession("admin@company.com"));
    }

    @Test
    void refreshSessionThrowsJwtExceptionWhenOperatorMissing() {
        when(repository.findByEmail("missing@company.com")).thenReturn(Optional.empty());

        assertThrows(JwtException.class, () -> service.refreshSession("missing@company.com"));
    }

    @Test
    void updateStatusChangesOperatorStatus() {
        DashboardOperator operator = buildOperator(DashboardOperatorStatus.ACTIVE);
        when(repository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(repository.save(any(DashboardOperator.class))).thenAnswer(inv -> inv.getArgument(0));

        DashboardResourceDto.OperatorItem updated = service.updateStatus(operator.getId(), DashboardOperatorStatus.BLOCKED);

        assertEquals(DashboardOperatorStatus.BLOCKED, updated.status());
    }

    @Test
    void updateStatusThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.updateStatus(id, DashboardOperatorStatus.BLOCKED));
    }

    private DashboardOperator buildOperator(DashboardOperatorStatus status) {
        DashboardOperator op = new DashboardOperator();
        op.setId(UUID.randomUUID());
        op.setEmail("admin@company.com");
        op.setPasswordHash("hashed");
        op.setAuthorities(List.of(DashboardOperatorAuthority.LOGS, DashboardOperatorAuthority.OPERATORS));
        op.setStatus(status);
        op.setLastRefreshedAt(fixedNow.minusSeconds(3600));
        return op;
    }
}
