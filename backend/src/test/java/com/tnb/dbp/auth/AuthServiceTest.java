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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private OtpChallengeRepository challenges;
    @Mock
    private SmsService sms;
    @Mock
    private SessionRepository sessions;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: fast unit tests
    private AuthService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new AuthService(customers, challenges, sms, sessions, encoder, 300, 3, 3, 300);
        customer = new Customer("TNB100001", "Arun Kumar", "9000000001", "arun.kumar@example.com",
                encoder.encode("Test@1234"));
    }

    @Test
    void validCredentialsSendOtpToRegisteredMobile() {
        when(customers.findByCustomerId("TNB100001")).thenReturn(Optional.of(customer));
        when(challenges.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpSentResponse response = service.login("TNB100001", "Test@1234");

        assertNotNull(response.challengeId());
        assertEquals("******0001", response.maskedMobile());
        assertEquals(300, response.otpExpiresInSeconds());
        verify(sms).send(eq("9000000001"), contains("OTP"));
    }

    @Test
    void wrongPasswordIsCountedAsFailedAttempt() {
        when(customers.findByCustomerId("TNB100001")).thenReturn(Optional.of(customer));

        ApiException ex = assertThrows(ApiException.class, () -> service.login("TNB100001", "Wrong@123"));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        assertEquals(1, customer.getFailedAttempts());
        assertFalse(customer.isLocked());
        verify(sms, never()).send(any(), any());
    }

    @Test
    void thirdWrongPasswordLocksTheAccount() {
        customer.setFailedAttempts(2);
        when(customers.findByCustomerId("TNB100001")).thenReturn(Optional.of(customer));

        ApiException ex = assertThrows(ApiException.class, () -> service.login("TNB100001", "Wrong@123"));

        assertEquals("ACCOUNT_LOCKED", ex.getCode());
        assertTrue(customer.isLocked());
        assertNotNull(customer.getLockedAt());
    }

    @Test
    void unknownCustomerGetsSameErrorAsWrongPassword() {
        when(customers.findByCustomerId("TNB999999")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.login("TNB999999", "Test@1234"));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }
}
