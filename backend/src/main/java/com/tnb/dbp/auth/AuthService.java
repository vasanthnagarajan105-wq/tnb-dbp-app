package com.tnb.dbp.auth;

import com.tnb.dbp.auth.AuthDtos.OtpSentResponse;
import com.tnb.dbp.auth.AuthDtos.TokenResponse;
import com.tnb.dbp.common.ApiException;
import com.tnb.dbp.customer.Customer;
import com.tnb.dbp.customer.CustomerRepository;
import com.tnb.dbp.otp.OtpChallenge;
import com.tnb.dbp.otp.OtpChallengeRepository;
import com.tnb.dbp.session.SessionRepository;
import com.tnb.dbp.session.UserSession;
import com.tnb.dbp.sms.SmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Customer login with password + OTP (TNB-4) and failed-attempt lock (TNB-3).
 */
@Service
public class AuthService {

    static final String PURPOSE_LOGIN = "LOGIN";

    private final CustomerRepository customers;
    private final OtpChallengeRepository challenges;
    private final SmsService sms;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final long otpValiditySeconds;
    private final int maxFailedAttempts;
    private final int maxOtpResends;
    private final long sessionValiditySeconds;
    private final SecureRandom random = new SecureRandom();

    public AuthService(CustomerRepository customers,
                       OtpChallengeRepository challenges,
                       SmsService sms,
                       SessionRepository sessions,
                       PasswordEncoder encoder,
                       @Value("${tnb.auth.otp-validity-seconds}") long otpValiditySeconds,
                       @Value("${tnb.auth.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${tnb.auth.max-otp-resends}") int maxOtpResends,
                       @Value("${tnb.auth.session-validity-seconds}") long sessionValiditySeconds) {
        this.customers = customers;
        this.challenges = challenges;
        this.sms = sms;
        this.sessions = sessions;
        this.encoder = encoder;
        this.otpValiditySeconds = otpValiditySeconds;
        this.maxFailedAttempts = maxFailedAttempts;
        this.maxOtpResends = maxOtpResends;
        this.sessionValiditySeconds = sessionValiditySeconds;
    }

    /** Step 1: check Customer ID and password, then send an OTP. */
    @Transactional(noRollbackFor = ApiException.class)
    public OtpSentResponse login(String customerId, String password) {
        Customer customer = customers.findByCustomerId(customerId)
                .orElseThrow(AuthService::invalidCredentials);

        if (customer.isLocked()) {
            throw accountLocked();
        }

        if (!encoder.matches(password, customer.getPasswordHash())) {
            boolean nowLocked = registerFailedAttempt(customer);
            throw nowLocked ? accountLocked() : invalidCredentials();
        }

        String otp = newOtp();
        OtpChallenge challenge = challenges.save(new OtpChallenge(
                customer, PURPOSE_LOGIN, encoder.encode(otp), Instant.now().plusSeconds(otpValiditySeconds)));
        sms.send(customer.getMobile(), otpMessage(otp));

        return new OtpSentResponse(challenge.getId(), customer.maskedMobile(), otpValiditySeconds,
                "OTP sent to your registered mobile number.");
    }

    /** Step 2: check the OTP and start a session. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse verifyOtp(UUID challengeId, String otp) {
        OtpChallenge challenge = findChallenge(challengeId);
        Customer customer = challenge.getCustomer();

        if (customer.isLocked()) {
            throw accountLocked();
        }
        if (challenge.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED", "This OTP has already been used. Please log in again.");
        }
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "OTP expired. Please resend.");
        }
        if (!encoder.matches(otp, challenge.getOtpHash())) {
            boolean nowLocked = registerFailedAttempt(customer);
            throw nowLocked ? accountLocked()
                    : new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OTP", "Invalid OTP.");
        }

        challenge.setUsed(true);
        customer.setUpdatedAt(Instant.now());

        Instant now = Instant.now();
        UserSession session = sessions.save(new UserSession(
                newToken(), customer, now, now.plusSeconds(sessionValiditySeconds)));

        return new TokenResponse(session.getToken(), "Bearer", sessionValiditySeconds,
                customer.getCustomerId(), customer.getFullName());
    }

    /** Sends a fresh OTP for an existing login attempt. */
    @Transactional(noRollbackFor = ApiException.class)
    public OtpSentResponse resendOtp(UUID challengeId) {
        OtpChallenge challenge = findChallenge(challengeId);
        Customer customer = challenge.getCustomer();

        if (customer.isLocked()) {
            throw accountLocked();
        }
        if (challenge.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_ALREADY_USED", "This OTP has already been used. Please log in again.");
        }
        if (challenge.getResendCount() > maxOtpResends) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_LIMIT_REACHED",
                    "Maximum OTP resends reached. Please log in again.");
        }

        String otp = newOtp();
        challenge.setOtpHash(encoder.encode(otp));
        challenge.setExpiresAt(Instant.now().plusSeconds(otpValiditySeconds));
        challenge.setResendCount(challenge.getResendCount() + 1);
        sms.send(customer.getMobile(), otpMessage(otp));

        return new OtpSentResponse(challenge.getId(), customer.maskedMobile(), otpValiditySeconds,
                "A new OTP has been sent to your registered mobile number.");
    }

    /** Counts a failed password or OTP attempt. Returns true if this attempt locked the account. */
    private boolean registerFailedAttempt(Customer customer) {
        Instant now = Instant.now();
        customer.setFailedAttempts(customer.getFailedAttempts() + 1);
        if (customer.getFailedAttempts() >= maxFailedAttempts) {
            customer.setLocked(true);
            customer.setLockedAt(now);
        }
        customer.setUpdatedAt(now);
        customers.save(customer);
        return customer.isLocked();
    }

    private OtpChallenge findChallenge(UUID challengeId) {
        return challenges.findById(challengeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND",
                        "OTP request not found. Please log in again."));
    }

    private String newOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String otpMessage(String otp) {
        return "Your TNB login OTP is " + otp + ". It is valid for " + (otpValiditySeconds / 60)
                + " minutes. Never share your OTP with anyone.";
    }

    private static ApiException invalidCredentials() {
        // Same message for unknown Customer ID and wrong password, so attackers cannot discover valid IDs.
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid Customer ID or password.");
    }

    private static ApiException accountLocked() {
        return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Your login is locked. Use 'Unlock account' or visit your branch.");
    }
}
