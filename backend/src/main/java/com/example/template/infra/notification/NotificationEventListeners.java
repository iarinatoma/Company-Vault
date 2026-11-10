package com.example.template.infra.notification;

import com.example.template.auth.event.AccountLockedEvent;
import com.example.template.auth.event.PasswordResetRequestedEvent;
import com.example.template.auth.event.PasswordSetupRequestedEvent;
import com.example.template.user.event.UserCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges domain events to the notification port after the publishing transaction commits —
 * an email about state that rolled back must never leave the building.
 */
@Component
public class NotificationEventListeners {

    private final NotificationPort notificationPort;

    public NotificationEventListeners(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @TransactionalEventListener
    public void onAccountLocked(AccountLockedEvent event) {
        notificationPort.sendEmail(
                event.email(),
                "Your account has been temporarily locked",
                "Too many failed sign-in attempts. The account unlocks at " + event.lockedUntil() + ".");
    }

    @TransactionalEventListener
    public void onUserCreated(UserCreatedEvent event) {
        notificationPort.sendEmail(event.email(), "Welcome", "Your account has been created.");
    }
    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        notificationPort.sendEmail(
                event.email(),
                "Reset your Company Vault password",
                "Someone asked to reset the password of this account. Open this link to choose a new one:\n"
                        + event.resetLink()
                        + "\n\nThe link works once and expires at " + event.expiresAt() + ".\n"
                        + "If you did not ask for this, ignore this e-mail — your password stays unchanged.");
    }

    @TransactionalEventListener
    public void onPasswordSetupRequested(PasswordSetupRequestedEvent event) {
        notificationPort.sendEmail(
                event.email(),
                "Set your Company Vault password",
                "Your account has been created. Open this one-time link to choose a password:\n"
                        + event.setupLink()
                        + "\n\nThe link works once and expires at " + event.expiresAt() + ".");
    }
}
