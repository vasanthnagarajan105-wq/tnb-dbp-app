package com.tnb.dbp.auth;

import com.tnb.dbp.auth.AuthDtos.LoginRequest;
import com.tnb.dbp.auth.AuthDtos.OtpSentResponse;
import com.tnb.dbp.auth.AuthDtos.ResendOtpRequest;
import com.tnb.dbp.auth.AuthDtos.TokenResponse;
import com.tnb.dbp.auth.AuthDtos.VerifyOtpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Customer login with password and OTP (TNB-4, TNB-3)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Step 1: verify Customer ID and password, send OTP")
    public OtpSentResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.customerId(), request.password());
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Step 2: verify OTP and receive an access token")
    public TokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request.challengeId(), request.otp());
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Send a new OTP for the same login attempt")
    public OtpSentResponse resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return authService.resendOtp(request.challengeId());
    }
}
