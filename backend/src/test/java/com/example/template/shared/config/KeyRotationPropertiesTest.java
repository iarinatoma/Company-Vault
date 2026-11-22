package com.example.template.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KeyRotationProperties (previous master key)")
class KeyRotationPropertiesTest {

    @Test
    @DisplayName("no previous key — the normal state — when unset or blank")
    void unsetOrBlank_meansNoRotation() {
        assertThat(new KeyRotationProperties(null).previousKeyBytes()).isNull();
        assertThat(new KeyRotationProperties("").previousKeyBytes()).isNull();
        assertThat(new KeyRotationProperties("   ").previousKeyBytes()).isNull();
    }

    @Test
    @DisplayName("accepts a base64 key of exactly 32 bytes")
    void validKey_isAccepted() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(new KeyRotationProperties(key).previousKeyBytes()).hasSize(32);
    }

    @Test
    @DisplayName("rejects a previous key that is not base64")
    void invalidBase64_isRejected() {
        assertThatThrownBy(() -> new KeyRotationProperties("not base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid base64");
    }

    @Test
    @DisplayName("rejects a previous key that is not 32 bytes long")
    void wrongLength_isRejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new KeyRotationProperties(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("application.properties only references the previous key, it never contains one")
    void applicationProperties_containsNoPreviousKey() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties).contains("app.security.encryption.previous-key=${ENCRYPTION_KEY_PREVIOUS:}");
    }
}