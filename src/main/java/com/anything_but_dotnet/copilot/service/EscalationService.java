package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.Citation;
import com.anything_but_dotnet.copilot.dto.EscalationResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationDetail;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationItem;
import com.anything_but_dotnet.copilot.entity.DashboardOperator;
import com.anything_but_dotnet.copilot.entity.Escalation;
import com.anything_but_dotnet.copilot.entity.EscalationDraft;
import com.anything_but_dotnet.copilot.entity.EscalationStatusHistory;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.exception.EscalationDraftExpiredException;
import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.repository.DashboardOperatorRepository;
import com.anything_but_dotnet.copilot.repository.EscalationDraftRepository;
import com.anything_but_dotnet.copilot.repository.EscalationRepository;
import com.anything_but_dotnet.copilot.repository.EscalationStatusHistoryRepository;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EscalationService {

    private static final Logger logger = LoggerFactory.getLogger(EscalationService.class);
    public static final Duration DRAFT_TTL = Duration.ofMinutes(15);
    private static final TypeReference<List<Citation>> CITATION_LIST_TYPE = new TypeReference<>() {};

    private final EscalationRepository escalationRepository;
    private final EscalationDraftRepository draftRepository;
    private final EscalationStatusHistoryRepository historyRepository;
    private final GenerationDiagnosticsRepository diagnosticsRepository;
    private final GenerationDiagnosticsService diagnosticsService;
    private final DashboardOperatorRepository operatorRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;

    public EscalationService(
            EscalationRepository escalationRepository,
            EscalationDraftRepository draftRepository,
            EscalationStatusHistoryRepository historyRepository,
            GenerationDiagnosticsRepository diagnosticsRepository,
            GenerationDiagnosticsService diagnosticsService,
            DashboardOperatorRepository operatorRepository,
            Clock clock,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations
    ) {
        this.escalationRepository = escalationRepository;
        this.draftRepository = draftRepository;
        this.historyRepository = historyRepository;
        this.diagnosticsRepository = diagnosticsRepository;
        this.diagnosticsService = diagnosticsService;
        this.operatorRepository = operatorRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
    }

    @Transactional
    public void saveDraft(
            String queryId,
            String requesterEmail,
            String questionText,
            List<Citation> sourceReferences
    ) {
        if (queryId == null || queryId.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        String sourceRefsJson = serializeSourceReferences(sourceReferences);
        EscalationDraft draft = new EscalationDraft(
                queryId,
                requesterEmail,
                questionText,
                sourceRefsJson,
                now,
                now.plus(DRAFT_TTL)
        );
        draftRepository.save(draft);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void purgeExpiredDrafts() {
        int deleted = draftRepository.deleteExpired(Instant.now(clock));
        if (deleted > 0) {
            logger.debug("Purged {} expired escalation draft(s).", deleted);
        }
    }

    public EscalationResponse createOrGet(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("query_id must not be blank.");
        }

        try {
            return transactionOperations.execute(status -> createOrGetInTransaction(queryId));
        } catch (DataIntegrityViolationException ex) {
            logger.debug("Concurrent escalation insert detected for queryId {}, reading existing record.", queryId);
            return escalationRepository.findByQueryId(queryId)
                    .map(EscalationResponse::from)
                    .orElseThrow(() -> ex);
        }
    }

    private EscalationResponse createOrGetInTransaction(String queryId) {
        // 1. Idempotency check first (even if draft has already been deleted/expired)
        Optional<Escalation> existing = escalationRepository.findByQueryId(queryId);
        if (existing.isPresent()) {
            return EscalationResponse.from(existing.get());
        }

        // 2. Verify question exists and did not fail
        GenerationDiagnostics diagnostics = diagnosticsService.findByQueryId(queryId)
                .orElseThrow(() -> new NotFoundException("Question not found for the provided query_id."));

        if (diagnostics.getOutcome() == GenerationOutcome.FAILURE) {
            throw new IllegalArgumentException("Cannot escalate a failed question request.");
        }

        // 3. Fetch active 15m TTL draft
        Instant now = Instant.now(clock);
        EscalationDraft draft = draftRepository.findByQueryIdAndExpiresAtAfter(queryId, now)
                .orElseThrow(() -> new EscalationDraftExpiredException(
                        "Escalation confirmation window (15m) has expired. Please ask your question again."
                ));

        // 4. Promote draft -> core.escalations, record history, mark escalated, and delete draft atomically
        Escalation escalation = Escalation.createPending(
                UUID.randomUUID(),
                queryId,
                draft.getRequesterEmail(),
                draft.getQuestionText(),
                draft.getSourceReferences(),
                now
        );
        escalationRepository.saveAndFlush(escalation);

        historyRepository.save(new EscalationStatusHistory(
                UUID.randomUUID(),
                escalation.getId(),
                null,
                EscalationStatus.PENDING,
                null,
                now
        ));

        diagnosticsService.markEscalated(queryId);
        draftRepository.delete(draft);

        return EscalationResponse.from(escalation);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public EscalationDetail getEscalationById(UUID id) {
        Escalation escalation = escalationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Escalation not found."));
        GenerationDiagnostics diag = diagnosticsService.findByQueryId(escalation.getQueryId()).orElse(null);
        List<EscalationStatusHistory> history = historyRepository.findByEscalationIdOrderByChangedAtAsc(id);
        List<Citation> sourceReferences = deserializeSourceReferences(escalation.getSourceReferences());
        return EscalationDetail.from(escalation, diag, sourceReferences, history);
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

    private String serializeSourceReferences(List<Citation> sourceReferences) {
        List<Citation> safeList = sourceReferences != null ? sourceReferences : List.of();
        try {
            return objectMapper.writeValueAsString(safeList);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize escalation source references.", e);
        }
    }

    private List<Citation> deserializeSourceReferences(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CITATION_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize escalation source references.", e);
        }
    }
}