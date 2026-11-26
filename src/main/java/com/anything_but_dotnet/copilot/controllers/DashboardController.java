package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAnalyticsDto.QuestionOutcomesAnalyticsResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardCommonDto.PageResponse;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationDetail;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.EscalationItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.SaveEscalationResponseRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardEscalationDto.UpdateEscalationStatusRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.GenerationLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardLogDto.IngestionLogItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.DocumentMetadataItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.OperatorItem;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UpdateOperatorStatusRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto.UserAclItem;
import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.SyncStatus;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import com.anything_but_dotnet.copilot.service.DashboardService;
import com.anything_but_dotnet.copilot.service.EscalationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dash")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardOperatorService operatorService;
    private final EscalationService escalationService;

    public DashboardController(
            DashboardService dashboardService,
            DashboardOperatorService operatorService,
            EscalationService escalationService
    ) {
        this.dashboardService = dashboardService;
        this.operatorService = operatorService;
        this.escalationService = escalationService;
    }

    @GetMapping("/logs/ingestion")
    public PageResponse<IngestionLogItem> getIngestionLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) SyncStatus status,
            @RequestParam(name = "document_id", required = false) String documentId
    ) {
        return dashboardService.getIngestionLogs(page, size, from, to, status, documentId);
    }

    @GetMapping("/logs/generation")
    public PageResponse<GenerationLogItem> getGenerationLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) GenerationOutcome outcome,
            @RequestParam(name = "error_category", required = false) ErrorCategory errorCategory
    ) {
        return dashboardService.getGenerationLogs(page, size, from, to, outcome, errorCategory);
    }

    @GetMapping("/analytics/outcomes")
    public QuestionOutcomesAnalyticsResponse getQuestionOutcomeAnalytics(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) ChatChannel channel
    ) {
        return dashboardService.getQuestionOutcomeAnalytics(from, to, channel);
    }

    @GetMapping("/analytics/prompts/{prompt_version_id}/outcomes")
    public QuestionOutcomesAnalyticsResponse getPromptOutcomeAnalytics(
            @PathVariable("prompt_version_id") UUID promptVersionId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) ChatChannel channel
    ) {
        return dashboardService.getPromptOutcomeAnalytics(promptVersionId, from, to, channel);
    }

    @GetMapping("/escalations")
    public PageResponse<EscalationItem> getEscalations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) EscalationStatus status,
            @RequestParam(name = "query_id", required = false) String queryId
    ) {
        return escalationService.getEscalations(page, size, from, to, status, queryId);
    }

    @GetMapping("/escalations/{id}")
    public EscalationDetail getEscalationById(@PathVariable UUID id) {
        return escalationService.getEscalationById(id);
    }

    @PatchMapping("/escalations/{id}/status")
    public EscalationItem updateEscalationStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEscalationStatusRequest request,
            Authentication authentication
    ) {
        String operatorEmail = authentication != null ? authentication.getName() : null;
        return escalationService.updateStatus(id, request.status(), operatorEmail);
    }

    @PutMapping("/escalations/{id}/response")
    public EscalationDetail saveEscalationResponse(
            @PathVariable UUID id,
            @Valid @RequestBody SaveEscalationResponseRequest request,
            Authentication authentication
    ) {
        String operatorEmail = authentication != null ? authentication.getName() : null;
        return escalationService.saveResponse(id, request.resolutionText(), request.status(), operatorEmail);
    }

    @GetMapping("/documents")
    public PageResponse<DocumentMetadataItem> getDocuments(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return dashboardService.getDocuments(page, size);
    }

    @GetMapping("/users")
    public PageResponse<UserAclItem> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return dashboardService.getUsers(page, size);
    }

    @GetMapping("/operators")
    public PageResponse<OperatorItem> getOperators(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(operatorService.getAll(pageable));
    }

    @PatchMapping("/operators/{id}/status")
    public OperatorItem updateOperatorStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOperatorStatusRequest request
    ) {
        return operatorService.updateStatus(id, request.status());
    }
}