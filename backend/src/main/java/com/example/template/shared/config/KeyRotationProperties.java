package com.example.template.shared.config;

import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Master key rotation: while {@code ENCRYPTION_KEY_PREVIOUS} is set, data still encrypted with
 * the old master key stays readable, and {@code POST /api/v1/keys/rotate-master} moves it to the
 * current key. Unset — the normal state — means no rotation is in progress.
 */
@ConfigurationProperties(prefix = "app.security.encryption")
public record KeyRotationProperties(@Nullable String previousKey) {

    public KeyRotationProperties {
        if (previousKey != null && !previousKey.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(previousKey.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("app.security.encryption.previous-key must be valid base64", e);
            }
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                        "app.security.encryption.previous-key must decode to exactly 32 bytes");
            }
        }
    }

    /** The previous master key, or {@code null} when no rotation is in progress. */
    public byte @Nullable [] previousKeyBytes() {
        if (previousKey == null || previousKey.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(previousKey.trim());
    }
}