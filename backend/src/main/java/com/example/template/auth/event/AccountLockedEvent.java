package com.example.template.auth.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the lockout counter locks an account. Lockout reaches its owner by email
 * via {@code NotificationPort} — never through the login response, which stays identical to
 * every other credential failure.
 */
public record AccountLockedEvent(UUID userId, String email, Instant lockedUntil) {}
