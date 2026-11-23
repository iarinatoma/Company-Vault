package com.example.template.secret.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Moves a secret to another folder in the same vault (no re-encryption) or into another vault
 * (decrypts with the old vault's DEK and re-encrypts with the target vault's DEK).
 * When {@code targetVaultId} is null, the secret stays in its current vault.
 * When {@code targetFolderId} is null, the secret is placed at the root of the target vault.
 */
public record MoveSecretRequest(
        @Nullable UUID targetVaultId,
        @Nullable UUID targetFolderId) {}