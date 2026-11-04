package com.tnb.dbp.session;

import com.tnb.dbp.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected UserSession() {
        // for JPA
    }

    public UserSession(String token, Customer customer, Instant createdAt, Instant expiresAt) {
        this.token = token;
        this.customer = customer;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
