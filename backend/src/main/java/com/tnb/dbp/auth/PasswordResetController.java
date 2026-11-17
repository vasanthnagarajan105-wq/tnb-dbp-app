package com.tnb.dbp.auth;

import com.tnb.dbp.auth.AuthDtos.OtpSentResponse;
import com.tnb.dbp.auth.PasswordResetDtos.MessageResponse;
import com.tnb.dbp.auth.PasswordResetDtos.ResetConfirmRequest;
import com.tnb.dbp.auth.PasswordResetDtos.ResetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "Password reset", description = "Reset a forgotten password and unlock the account with an OTP (TNB-5)")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    @Operation(summary = "Step 1: verify Customer ID and registered mobile, send OTP")
    public OtpSentResponse request(@Valid @RequestBody ResetRequest request) {
        return passwordResetService.requestReset(request.customerId(), request.mobile());
    }

    @PostMapping("/confirm")
    @Operation(summary = "Step 2: verify OTP and set a new password (also unlocks the account)")
    public MessageResponse confirm(@Valid @RequestBody ResetConfirmRequest request) {
        return passwordResetService.confirmReset(request.challengeId(), request.otp(), request.newPassword());
    }
}
