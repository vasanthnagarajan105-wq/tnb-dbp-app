package com.tnb.dbp.sms;

import org.springframework.stereotype.Service;

/**
 * Simulated SMS gateway for the MVP (RFP FR-NOT-01).
 * Messages are written to the sms_outbox table instead of being sent to a phone.
 */
@Service
public class SmsService {

    private final SmsOutboxRepository outbox;

    public SmsService(SmsOutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void send(String mobile, String message) {
        outbox.save(new SmsMessage(mobile, message));
    }
}
