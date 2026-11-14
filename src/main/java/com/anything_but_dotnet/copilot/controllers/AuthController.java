package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.dto.auth.AccessTokenPayload;
import com.anything_but_dotnet.copilot.dto.auth.RefreshTokenPayload;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardAuthDto;
import com.anything_but_dotnet.copilot.dto.dashboard.DashboardResourceDto;
import com.anything_but_dotnet.copilot.security.JwtService;
import com.anything_but_dotnet.copilot.security.OperatorDetailsImpl;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/dash/auth")
public class AuthController {
    private final DashboardOperatorService dashboardOperatorService;
    private final JwtService jwtService;
    private final AuthenticationProvider authenticationProvider;

    public AuthController(
            DashboardOperatorService dashboardOperatorService,
            JwtService jwtService,
            AuthenticationProvider authenticationProvider
    ) {
        this.dashboardOperatorService = dashboardOperatorService;
        this.jwtService = jwtService;
        this.authenticationProvider = authenticationProvider;
    }

    @PostMapping("/login")
    public DashboardAuthDto.TokensResponse login(@Valid @RequestBody DashboardAuthDto.LoginRequest loginRequest) {
        var authentication = new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password());
        var authenticated = authenticationProvider.authenticate(authentication);
        OperatorDetailsImpl principal = (OperatorDetailsImpl) Objects.requireNonNull(authenticated.getPrincipal());

        var accessTokenPayload = new AccessTokenPayload(principal.email(), principal.status(), principal.authorities());
        var refreshTokenPayload = new RefreshTokenPayload(principal.email());

        return issueTokens(accessTokenPayload, refreshTokenPayload);
    }

    @PostMapping("/refresh")
    public DashboardAuthDto.TokensResponse refresh(
            @Valid @RequestBody DashboardAuthDto.RefreshTokenRequest refreshRequest
    ) {
        RefreshTokenPayload parsedRefresh = jwtService.parseRefreshToken(refreshRequest.refreshToken());
        DashboardResourceDto.OperatorItem operator = dashboardOperatorService.refreshSession(parsedRefresh.email());

        var accessTokenPayload = new AccessTokenPayload(operator.email(), operator.status(), operator.authorities());
        var refreshTokenPayload = new RefreshTokenPayload(operator.email());

        return issueTokens(accessTokenPayload, refreshTokenPayload);
    }

    private DashboardAuthDto.TokensResponse issueTokens(
            AccessTokenPayload accessTokenPayload,
            RefreshTokenPayload refreshTokenPayload
    ) {
        String accessToken = jwtService.generateAccessToken(accessTokenPayload);
        String refreshToken = jwtService.generateRefreshToken(refreshTokenPayload);
        return new DashboardAuthDto.TokensResponse(
                accessToken,
                jwtService.getExpirationOfAccessToken(),
                refreshToken,
                jwtService.getExpirationOfRefreshToken()
        );
    }
}
