package com.example.template.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new general vault. {@code ownerId} is the user who gets MANAGE on it — the admin creating
 * the vault gets no access to its secrets unless they name themselves.
 */
public record CreateVaultRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) @Nullable String description,
        @NotNull UUID ownerId) {}