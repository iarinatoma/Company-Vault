package com.example.template.infra.notification;

/**
 * Outbound notification boundary. Business flows depend on this port, never on a concrete
 * mail/SMS integration — swap the adapter, not the callers.
 */
public interface NotificationPort {

    void sendEmail(String recipient, String subject, String body);
}
