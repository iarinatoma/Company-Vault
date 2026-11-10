package com.example.template.auth.event;

import java.time.Instant;
import java.util.UUID;

public record PasswordSetupRequestedEvent(UUID userId, String email, String setupLink, Instant expiresAt) {}
