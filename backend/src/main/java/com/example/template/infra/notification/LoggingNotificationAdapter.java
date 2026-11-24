package com.example.template.infra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local development adapter: logs notification subjects instead of sending mail.
 */
@Component
@ConditionalOnProperty(prefix = "app.notification.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void sendEmail(String recipient, String subject, String body) {
        log.info("Email to {}: {}", recipient, subject);
    }
}
