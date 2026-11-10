package com.example.template.infra.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notification.email", name = "enabled", havingValue = "true")
public class SmtpNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationAdapter(
            JavaMailSender mailSender, @Value("${app.notification.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendEmail(String recipient, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
