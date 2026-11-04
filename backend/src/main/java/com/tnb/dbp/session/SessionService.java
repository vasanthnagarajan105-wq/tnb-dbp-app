package com.tnb.dbp.session;

import com.tnb.dbp.common.ApiException;
import com.tnb.dbp.customer.Customer;
import com.tnb.dbp.customer.CustomerProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SessionService {

    private static final String BEARER = "Bearer ";

    private final SessionRepository sessions;

    public SessionService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Resolves the logged-in customer from an "Authorization: Bearer <token>" header. */
    @Transactional(readOnly = true)
    public CustomerProfile currentCustomer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            throw unauthorized("Missing or invalid access token.");
        }
        String token = authorizationHeader.substring(BEARER.length()).trim();

        UserSession session = sessions.findById(token)
                .orElseThrow(() -> unauthorized("Missing or invalid access token."));

        if (Instant.now().isAfter(session.getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session expired. Please log in again.");
        }

        Customer c = session.getCustomer();
        return new CustomerProfile(c.getCustomerId(), c.getFullName(), c.maskedMobile(), c.getEmail());
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
