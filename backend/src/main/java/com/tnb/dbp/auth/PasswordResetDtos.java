package com.tnb.dbp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Request and response bodies for the password reset API (TNB-5). */
public final class PasswordResetDtos {

    private PasswordResetDtos() {
    }

    public record ResetRequest(
            @NotBlank String customerId,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be 10 digits") String mobile) {
    }

    public record ResetConfirmRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String otp,
            @NotBlank String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
