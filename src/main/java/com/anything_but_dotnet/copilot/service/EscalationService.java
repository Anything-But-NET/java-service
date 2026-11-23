package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.EscalationResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationDetail;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationItem;
import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.repository.DashboardOperatorRepository;
import com.anything_but_dotnet.copilot.repository.EscalationRepository;
import com.anything_but_dotnet.copilot.repository.EscalationStatusHistoryRepository;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EscalationService {

    private final EscalationRepository escalationRepository;
    private final EscalationStatusHistoryRepository historyRepository;
    private final GenerationDiagnosticsRepository diagnosticsRepository;
    private final GenerationDiagnosticsService diagnosticsService;
    private final DashboardOperatorRepository operatorRepository;
    private final Clock clock;

    public EscalationService(
            EscalationRepository escalationRepository,
            EscalationStatusHistoryRepository historyRepository,
            GenerationDiagnosticsRepository diagnosticsRepository,
            GenerationDiagnosticsService diagnosticsService,
            DashboardOperatorRepository operatorRepository,
            Clock clock
    ) {
        this.escalationRepository = escalationRepository;
        this.historyRepository = historyRepository;
        this.diagnosticsRepository = diagnosticsRepository;
        this.diagnosticsService = diagnosticsService;
        this.operatorRepository = operatorRepository;
        this.clock = clock;
    }

    @Transactional
    public EscalationResponse createOrGet(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("query_id must not be blank.");
        }

        Optional<Escalation> existing = escalationRepository.findByQueryId(queryId);
        if (existing.isPresent()) {
            return EscalationResponse.from(existing.get());
        }

        GenerationDiagnostics diagnostics = diagnosticsService.findByQueryId(queryId)
                .orElseThrow(() -> new NotFoundException("Question not found for the provided query_id."));

        if (diagnostics.getOutcome() == GenerationOutcome.FAILURE) {
            throw new IllegalArgumentException("Cannot escalate a failed question request.");
        }

        diagnosticsService.markEscalated(queryId);

        Instant now = Instant.now(clock);
        Escalation escalation = Escalation.createPending(UUID.randomUUID(), queryId, now);
        escalationRepository.save(escalation);

        historyRepository.save(new EscalationStatusHistory(
                UUID.randomUUID(),
                escalation.getId(),
                null,
                EscalationStatus.PENDING,
                null,
                now
        ));

        return EscalationResponse.from(escalation);
    }

    public PageResponse<EscalationItem> getEscalations(
            int page,
            int size,
            Instant from,
            Instant to,
            EscalationStatus status,
            String queryId
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Query parameter 'from' must not be after 'to'.");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Escalation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (queryId != null && !queryId.isBlank()) {
                predicates.add(cb.equal(root.get("queryId"), queryId.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var resultPage = escalationRepository.findAll(spec, pageable);
        List<String> queryIds = resultPage.getContent().stream()
                .map(Escalation::getQueryId)
                .toList();
        Map<String, GenerationDiagnostics> diagByQueryId = queryIds.isEmpty()
                ? Map.of()
                : diagnosticsRepository.findByQueryIdIn(queryIds).stream()
                .collect(Collectors.toMap(GenerationDiagnostics::getQueryId, Function.identity()));

        return PageResponse.from(
                resultPage,
                esc -> EscalationItem.from(esc, diagByQueryId.get(esc.getQueryId()))
        );
    }

    public EscalationDetail getEscalationById(UUID id) {
        Escalation escalation = escalationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Escalation not found."));
        GenerationDiagnostics diag = diagnosticsService.findByQueryId(escalation.getQueryId()).orElse(null);
        List<EscalationStatusHistory> history = historyRepository.findByEscalationIdOrderByChangedAtAsc(id);
        return EscalationDetail.from(escalation, diag, history);
    }

    @Transactional
    public EscalationItem updateStatus(UUID id, EscalationStatus targetStatus, String operatorEmail) {
        Escalation escalation = escalationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Escalation not found."));

        if (escalation.getStatus() == targetStatus) {
            GenerationDiagnostics diag = diagnosticsService.findByQueryId(escalation.getQueryId()).orElse(null);
            return EscalationItem.from(escalation, diag);
        }

        EscalationStatus previousStatus = escalation.getStatus();
        Instant now = Instant.now(clock);
        escalation.transitionTo(targetStatus, now);
        escalationRepository.save(escalation);

        UUID operatorId = (operatorEmail != null && !operatorEmail.isBlank())
                ? operatorRepository.findByEmail(operatorEmail).map(DashboardOperator::getId).orElse(null)
                : null;

        historyRepository.save(new EscalationStatusHistory(
                UUID.randomUUID(),
                escalation.getId(),
                previousStatus,
                targetStatus,
                operatorId,
                now
        ));

        GenerationDiagnostics diag = diagnosticsService.findByQueryId(escalation.getQueryId()).orElse(null);
        return EscalationItem.from(escalation, diag);
    }
}