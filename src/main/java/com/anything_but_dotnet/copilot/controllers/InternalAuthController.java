package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.dto.auth.AccessTokenPayload;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.TokenIntrospectRequest;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto.TokenIntrospectResponse;
import com.anything_but_dotnet.copilot.exception.JwtException;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.anything_but_dotnet.copilot.security.JwtService;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/auth")
public class InternalAuthController {

    private final JwtService jwtService;
    private final DashboardOperatorService operatorService;

    public InternalAuthController(JwtService jwtService, DashboardOperatorService operatorService) {
        this.jwtService = jwtService;
        this.operatorService = operatorService;
    }

    @PostMapping("/introspect")
    public TokenIntrospectResponse introspect(@Valid @RequestBody TokenIntrospectRequest request) {
        AccessTokenPayload payload;
        try {
            payload = jwtService.parseAccessToken(request.token());
        } catch (JwtException ex) {
            return TokenIntrospectResponse.inactive();
        }

        if (payload.status() == DashboardOperatorStatus.BLOCKED) {
            return TokenIntrospectResponse.inactive();
        }

        return operatorService.findByEmail(payload.email())
                .filter(op -> op.status() == DashboardOperatorStatus.ACTIVE)
                .map(op -> new TokenIntrospectResponse(
                        true,
                        op.id(),
                        op.email(),
                        op.status(),
                        op.authorities()
                ))
                .orElseGet(TokenIntrospectResponse::inactive);
    }
}
