package com.anything_but_dotnet.copilot.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Authentication DTOs for {@code /api/v1/dash/auth/*}.
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
}
