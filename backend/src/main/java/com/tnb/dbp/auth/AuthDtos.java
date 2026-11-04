package com.tnb.dbp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Request and response bodies for the authentication API. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String customerId,
            @NotBlank String password) {
    }

    public record VerifyOtpRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String otp) {
    }

    public record ResendOtpRequest(
            @NotNull UUID challengeId) {
    }

    public record OtpSentResponse(
            UUID challengeId,
            String maskedMobile,
            long otpExpiresInSeconds,
            String message) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String customerId,
            String fullName) {
    }
}
