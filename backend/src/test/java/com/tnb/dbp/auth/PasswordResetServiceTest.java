package com.tnb.dbp.auth;

import com.tnb.dbp.auth.AuthDtos.OtpSentResponse;
import com.tnb.dbp.common.ApiException;
import com.tnb.dbp.customer.Customer;
import com.tnb.dbp.customer.CustomerRepository;
import com.tnb.dbp.otp.OtpChallenge;
import com.tnb.dbp.otp.OtpChallengeRepository;
import com.tnb.dbp.session.SessionRepository;
import com.tnb.dbp.sms.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private OtpChallengeRepository challenges;
    @Mock
    private SessionRepository sessions;
    @Mock
    private SmsService sms;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: fast unit tests
    private PasswordResetService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(customers, challenges, sessions, sms, encoder, 300, 3);
        customer = new Customer("TNB100003", "Karthik S", "9000000003", "karthik.s@example.com",
                encoder.encode("Test@1234"));
        customer.setFailedAttempts(3);
        customer.setLocked(true);
        customer.setLockedAt(Instant.now());
    }

    private OtpChallenge resetChallenge(String otp) {
        return new OtpChallenge(customer, PasswordResetService.PURPOSE_PASSWORD_RESET, encoder.encode(otp),
                Instant.now().plusSeconds(300));
    }

    @Test
    void matchingDetailsSendResetOtp() {
        when(customers.findByCustomerId("TNB100003")).thenReturn(Optional.of(customer));
        when(challenges.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpSentResponse response = service.requestReset("TNB100003", "9000000003");

        assertNotNull(response.challengeId());
        assertEquals("******0003", response.maskedMobile());
        verify(sms).send(eq("9000000003"), contains("password reset OTP"));
    }

    @Test
    void wrongMobileGetsGenericError() {
        when(customers.findByCustomerId("TNB100003")).thenReturn(Optional.of(customer));

        ApiException ex = assertThrows(ApiException.class, () -> service.requestReset("TNB100003", "9999999999"));

        assertEquals("INVALID_DETAILS", ex.getCode());
        verify(sms, never()).send(any(), any());
    }

    @Test
    void unknownCustomerGetsSameGenericError() {
        when(customers.findByCustomerId("TNB999999")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.requestReset("TNB999999", "9000000003"));

        assertEquals("INVALID_DETAILS", ex.getCode());
    }

    @Test
    void validOtpAndStrongPasswordChangesPasswordAndUnlocks() {
        OtpChallenge challenge = resetChallenge("123456");
        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        service.confirmReset(challenge.getId(), "123456", "NewPass@123");

        assertTrue(encoder.matches("NewPass@123", customer.getPasswordHash()));
        assertFalse(customer.isLocked());
        assertNull(customer.getLockedAt());
        assertEquals(0, customer.getFailedAttempts());
        assertTrue(challenge.isUsed());
        verify(sessions).deleteAllForCustomer(customer);
        verify(sms).send(eq("9000000003"), contains("password was changed"));
    }

    @Test
    void weakPasswordIsRejectedAndOtpIsNotConsumed() {
        OtpChallenge challenge = resetChallenge("123456");
        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmReset(challenge.getId(), "123456", "password"));

        assertEquals("WEAK_PASSWORD", ex.getCode());
        assertFalse(challenge.isUsed());
        assertTrue(customer.isLocked());
    }

    @Test
    void sameAsCurrentPasswordIsRejected() {
        OtpChallenge challenge = resetChallenge("123456");
        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmReset(challenge.getId(), "123456", "Test@1234"));

        assertEquals("SAME_AS_OLD_PASSWORD", ex.getCode());
    }

    @Test
    void thirdWrongOtpCancelsTheRequestWithoutTouchingLoginCounter() {
        OtpChallenge challenge = resetChallenge("123456");
        challenge.setVerifyAttempts(2);
        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmReset(challenge.getId(), "000000", "NewPass@123"));

        assertEquals("OTP_ATTEMPTS_EXCEEDED", ex.getCode());
        assertEquals(3, challenge.getVerifyAttempts());
        assertEquals(3, customer.getFailedAttempts());   // login counter unchanged (AC5)
    }

    @Test
    void loginChallengeCannotBeUsedForPasswordReset() {
        OtpChallenge loginChallenge = new OtpChallenge(customer, "LOGIN", encoder.encode("123456"),
                Instant.now().plusSeconds(300));
        when(challenges.findById(loginChallenge.getId())).thenReturn(Optional.of(loginChallenge));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.confirmReset(loginChallenge.getId(), "123456", "NewPass@123"));

        assertEquals("CHALLENGE_NOT_FOUND", ex.getCode());
    }
}
