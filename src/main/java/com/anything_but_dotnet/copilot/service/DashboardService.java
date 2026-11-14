package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.GenerationLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.DocumentMetadataItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UserAclItem;
import com.anything_but_dotnet.copilot.entity.DocumentSyncRun;
import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.repository.DocumentMetadataRepository;
import com.anything_but_dotnet.copilot.repository.DocumentSyncRunRepository;
import com.anything_but_dotnet.copilot.repository.GenerationDiagnosticsRepository;
import com.anything_but_dotnet.copilot.repository.UsersRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only query service backing Web Dashboard endpoints.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DocumentSyncRunRepository syncRunRepository;
    private final GenerationDiagnosticsRepository diagnosticsRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UsersRepository usersRepository;

    public DashboardService(
            DocumentSyncRunRepository syncRunRepository,
            GenerationDiagnosticsRepository diagnosticsRepository,
            DocumentMetadataRepository documentMetadataRepository,
            UsersRepository usersRepository
    ) {
        this.syncRunRepository = syncRunRepository;
        this.diagnosticsRepository = diagnosticsRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.usersRepository = usersRepository;
    }

    public PageResponse<IngestionLogItem> getIngestionLogs(
            int page,
            int size,
            Instant from,
            Instant to,
            SyncStatus status,
            String documentId
    ) {
        validateTimeRange(from, to);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Specification<DocumentSyncRun> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (documentId != null && !documentId.isBlank()) {
                predicates.add(cb.equal(root.get("documentId"), documentId.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(syncRunRepository.findAll(spec, pageable), IngestionLogItem::from);
    }

    public PageResponse<GenerationLogItem> getGenerationLogs(
            int page,
            int size,
            Instant from,
            Instant to,
            GenerationOutcome outcome,
            ErrorCategory errorCategory
    ) {
        validateTimeRange(from, to);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<GenerationDiagnostics> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (errorCategory != null) {
                predicates.add(cb.equal(root.get("errorCategory"), errorCategory));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(diagnosticsRepository.findAll(spec, pageable), GenerationLogItem::from);
    }

    public PageResponse<DocumentMetadataItem> getDocuments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncAt"));
        return PageResponse.from(documentMetadataRepository.findAll(pageable), DocumentMetadataItem::from);
    }

    public PageResponse<UserAclItem> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncAt"));
        return PageResponse.from(usersRepository.findAll(pageable), UserAclItem::from);
    }

    private static void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Query parameter 'from' must not be after 'to'.");
        }
    }
}
