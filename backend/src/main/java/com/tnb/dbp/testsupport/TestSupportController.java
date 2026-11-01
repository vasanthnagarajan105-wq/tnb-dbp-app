package com.tnb.dbp.testsupport;

import com.tnb.dbp.sms.SmsOutboxRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Test hooks for automation. Exists ONLY in dev and sit profiles - never deployed to UAT or production.
 * Lets tests read the OTP that the simulated SMS gateway "sent".
 */
@RestController
@Profile({"dev", "sit"})
@RequestMapping("/api/v1/test-support")
@Tag(name = "Test support (non-production only)")
public class TestSupportController {

    private final SmsOutboxRepository outbox;

    public TestSupportController(SmsOutboxRepository outbox) {
        this.outbox = outbox;
    }

    public record SmsView(String mobile, String message, Instant sentAt) {
    }

    @GetMapping("/sms")
    @Operation(summary = "Latest 5 simulated SMS messages for a mobile number (newest first)")
    public List<SmsView> latestSms(@RequestParam String mobile) {
        return outbox.findTop5ByMobileOrderBySentAtDesc(mobile).stream()
                .map(m -> new SmsView(m.getMobile(), m.getMessage(), m.getSentAt()))
                .toList();
    }
}
