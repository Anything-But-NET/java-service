package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.dto.CreateEscalationRequest;
import com.anything_but_dotnet.copilot.dto.EscalationResponse;
import com.anything_but_dotnet.copilot.dto.UserQuestionRequest;
import com.anything_but_dotnet.copilot.dto.UserQuestionResponse;
import com.anything_but_dotnet.copilot.service.EscalationService;
import com.anything_but_dotnet.copilot.service.RagGenerationOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class GenerationController {
    private final RagGenerationOrchestrator ragGenerationOrchestrator;
    private final EscalationService escalationService;

    public GenerationController(
            RagGenerationOrchestrator ragGenerationOrchestrator,
            EscalationService escalationService
    ) {
        this.ragGenerationOrchestrator = ragGenerationOrchestrator;
        this.escalationService = escalationService;
    }

    @PostMapping("/question")
    public ResponseEntity<UserQuestionResponse> processUserQuestion(
            @Valid @RequestBody UserQuestionRequest request
    ) {
        return ResponseEntity.ok(ragGenerationOrchestrator.generateUserQuestions(request));
    }

    @PostMapping("/escalations")
    public ResponseEntity<EscalationResponse> createOrGetEscalation(
            @Valid @RequestBody CreateEscalationRequest request
    ) {
        return ResponseEntity.ok(escalationService.createOrGet(request.queryId()));
    }
}