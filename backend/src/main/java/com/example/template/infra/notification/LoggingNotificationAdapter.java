package com.example.template.infra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Template placeholder adapter: logs instead of sending. Replace with a real mail
 * integration; the port's callers don't change.
 */
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void sendEmail(String recipient, String subject, String body) {
        log.info("Email to {}: {}", recipient, subject);
    }
}
