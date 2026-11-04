package com.tnb.dbp.customer;

import com.tnb.dbp.config.AppConfig;
import com.tnb.dbp.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer", description = "Logged-in customer details")
public class CustomerController {

    private final SessionService sessionService;

    public CustomerController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in customer's profile",
            security = @SecurityRequirement(name = AppConfig.BEARER_AUTH))
    public CustomerProfile me(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return sessionService.currentCustomer(authorization);
    }
}
