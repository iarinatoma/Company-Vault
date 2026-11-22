package com.example.template.vault.dto;

import com.example.template.shared.security.AccessLevel;
import com.example.template.vault.model.VaultType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Vault metadata plus the caller's own access level, so the frontend knows which buttons to show. */
public record VaultResponse(
        UUID id,
        String name,
        @Nullable String description,
        VaultType type,
        boolean archived,
        Instant createdAt,
        AccessLevel accessLevel) {}