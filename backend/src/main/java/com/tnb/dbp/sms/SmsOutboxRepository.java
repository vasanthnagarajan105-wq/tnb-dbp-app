package com.tnb.dbp.sms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmsOutboxRepository extends JpaRepository<SmsMessage, Long> {

    List<SmsMessage> findTop5ByMobileOrderBySentAtDesc(String mobile);
}
