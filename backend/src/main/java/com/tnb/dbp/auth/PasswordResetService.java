package com.tnb.dbp.auth;

import com.tnb.dbp.auth.AuthDtos.OtpSentResponse;
import com.tnb.dbp.auth.PasswordResetDtos.MessageResponse;
import com.tnb.dbp.common.ApiException;
import com.tnb.dbp.customer.Customer;
import com.tnb.dbp.customer.CustomerRepository;
import com.tnb.dbp.otp.OtpChallenge;
import com.tnb.dbp.otp.OtpChallengeRepository;
import com.tnb.dbp.session.SessionRepository;
import com.tnb.dbp.sms.SmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reset a forgotten password with an OTP to the registered mobile (TNB-5).
 * A successful reset also unlocks the account and logs the customer out everywhere.
 */
@Service
public class PasswordResetService {

    static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    /** 8-20 characters, no spaces, at least one lowercase, uppercase, digit and special character (AC4). */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");

    private final CustomerRepository customers;
    private final OtpChallengeRepository challenges;
    private final SessionRepository sessions;
    private final SmsService sms;
    private final PasswordEncoder encoder;
    private final long otpValiditySeconds;
    private final int maxResetOtpAttempts;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(CustomerRepository customers,
                                OtpChallengeRepository challenges,
                                SessionRepository sessions,
                                SmsService sms,
                                PasswordEncoder encoder,
                                @Value("${tnb.auth.otp-validity-seconds}") long otpValiditySeconds,
                                @Value("${tnb.auth.max-reset-otp-attempts}") int maxResetOtpAttempts) {
        this.customers = customers;
        this.challenges = challenges;
        this.sessions = sessions;
        this.sms = sms;
        this.encoder = encoder;
        this.otpValiditySeconds = otpValiditySeconds;
        this.maxResetOtpAttempts = maxResetOtpAttempts;
    }

    /** Step 1: Customer ID + registered mobile must match, then an OTP is sent (AC1, AC2). */
    @Transactional
    public OtpSentResponse requestReset(String customerId, String mobile) {
        Customer customer = customers.findByCustomerId(customerId)
                .filter(c -> c.getMobile().equals(mobile))
                .orElseThrow(PasswordResetService::invalidDetails);

        String otp = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge challenge = challenges.save(new OtpChallenge(
                customer, PURPOSE_PASSWORD_RESET, encoder.encode(otp),
                Instant.now().plusSeconds(otpValiditySeconds)));
        sms.send(customer.getMobile(), "Your TNB password reset OTP is " + otp + ". It is valid for "
                + (otpValiditySeconds / 60) + " minutes. Never share your OTP with anyone.");

        return new OtpSentResponse(challenge.getId(), customer.maskedMobile(), otpValiditySeconds,
                "OTP sent to your registered mobile number.");
    }

    /** Step 2: valid OTP + a policy-compliant new password changes the password and unlocks (AC3-AC7). */
    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse confirmReset(UUID challengeId, String otp, String newPassword) {
        OtpChallenge challenge = challenges.findById(challengeId)
                .filter(c -> PURPOSE_PASSWORD_RESET.equals(c.getPurpose()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND",
                        "Password reset request not found. Please start again."));

        if (challenge.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED",
                    "This OTP has already been used. Please start again.");
        }
        if (challenge.getVerifyAttempts() >= maxResetOtpAttempts) {
            throw otpAttemptsExceeded();
        }
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "OTP expired. Please start again.");
        }

        // The OTP is checked BEFORE the password rules, so nobody without the OTP can probe the current password.
        if (!encoder.matches(otp, challenge.getOtpHash())) {
            challenge.setVerifyAttempts(challenge.getVerifyAttempts() + 1);
            challenges.save(challenge);
            // AC5: wrong reset OTPs cancel the request; they do NOT count toward the login lock.
            if (challenge.getVerifyAttempts() >= maxResetOtpAttempts) {
                throw otpAttemptsExceeded();
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OTP", "Invalid OTP.");
        }

        Customer customer = challenge.getCustomer();
        if (!PASSWORD_POLICY.matcher(newPassword).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "Password must be 8-20 characters with at least one uppercase letter, one lowercase letter, "
                            + "one digit and one special character, and no spaces.");
        }
        if (encoder.matches(newPassword, customer.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_AS_OLD_PASSWORD",
                    "New password must be different from the current password.");
        }

        Instant now = Instant.now();
        challenge.setUsed(true);
        customer.setPasswordHash(encoder.encode(newPassword));
        customer.setFailedAttempts(0);        // AC3
        customer.setLocked(false);            // AC3: reset also unlocks
        customer.setLockedAt(null);
        customer.setUpdatedAt(now);
        customers.save(customer);
        sessions.deleteAllForCustomer(customer);   // AC6: logged out everywhere
        sms.send(customer.getMobile(),
                "Your TNB password was changed. If this was not you, contact the bank immediately.");

        return new MessageResponse("Password changed. Please log in with your new password.");
    }

    private static ApiException invalidDetails() {
        // Same error whether the Customer ID is unknown or the mobile does not match (AC2).
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DETAILS", "Details do not match our records.");
    }

    private static ApiException otpAttemptsExceeded() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_ATTEMPTS_EXCEEDED",
                "Too many wrong OTPs. Please start the password reset again.");
    }
}
