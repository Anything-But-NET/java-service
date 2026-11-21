package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Authentication DTOs for {@code /api/v1/dash/auth/*} and {@code /api/v1/internal/auth/*}.
 */
public interface DashboardAuthDto {

    record LoginRequest(
            @NotBlank
            @Email
            @JsonProperty("email")
            String email,

            @NotBlank
            @JsonProperty("password")
            String password
    ) {
    }

    record RefreshTokenRequest(
            @NotBlank
            @JsonProperty("refresh_token")
            String refreshToken
    ) {
    }

    record TokensResponse(
            @NotBlank
            @JsonProperty("access_token")
            String accessToken,

            @Positive
            @JsonProperty("access_expires_in")
            long accessExpiresIn,

            @NotBlank
            @JsonProperty("refresh_token")
            String refreshToken,

            @Positive
            @JsonProperty("refresh_expires_in")
            long refreshExpiresIn
    ) {
    }

    record TokenIntrospectRequest(
            @NotBlank
            @JsonProperty("token")
            String token
    ) {
    }

    record TokenIntrospectResponse(
            @JsonProperty("active")
            boolean active,

            @JsonProperty("operator_id")
            UUID operatorId,

            @JsonProperty("email")
            String email,

            @JsonProperty("status")
            DashboardOperatorStatus status,

            @JsonProperty("authorities")
            List<DashboardOperatorAuthority> authorities
    ) {
        public static TokenIntrospectResponse inactive() {
            return new TokenIntrospectResponse(false, null, null, null, List.of());
        }
    }
}
