package com.example.template.auth.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a reset link is issued. The link carries the raw token, so it reaches the
 * user only by e-mail via {@code NotificationPort} — never a response body, never a log line.
 */
public record PasswordResetRequestedEvent(UUID userId, String email, String resetLink, Instant expiresAt) {}