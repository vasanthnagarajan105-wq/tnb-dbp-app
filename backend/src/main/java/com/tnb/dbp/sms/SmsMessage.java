package com.tnb.dbp.sms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A message "sent" by the simulated SMS gateway. */
@Entity
@Table(name = "sms_outbox")
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mobile;

    @Column(nullable = false)
    private String message;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected SmsMessage() {
        // for JPA
    }

    public SmsMessage(String mobile, String message) {
        this.mobile = mobile;
        this.message = message;
        this.sentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public String getMessage() {
        return message;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
