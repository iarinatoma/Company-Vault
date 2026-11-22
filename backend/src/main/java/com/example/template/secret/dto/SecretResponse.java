package com.example.template.secret.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A secret's metadata — never its value (spec 4.3: masked by default; the value needs an explicit reveal). */
public record SecretResponse(
        UUID id,
        UUID vaultId,
        String name,
        @Nullable String ownerEmail,
        @Nullable String updatedByEmail,
        Instant createdAt,
        Instant updatedAt) {}