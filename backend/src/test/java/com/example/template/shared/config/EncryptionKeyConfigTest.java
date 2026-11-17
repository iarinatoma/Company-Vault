package com.example.template.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.ConfigTreePropertySource;

class EncryptionKeyConfigTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("accepts a base64 key of exactly 32 bytes")
    void validKey_isAccepted() {
        var encryption = new AppSecurityProperties.Encryption(VALID_KEY);

        assertThat(encryption.keyBytes()).hasSize(32);
    }

    @Test
    @DisplayName("rejects a key that is not base64")
    void invalidBase64_isRejected() {
        assertThatThrownBy(() -> new AppSecurityProperties.Encryption("not base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid base64");
    }

    @Test
    @DisplayName("rejects a key that is not 32 bytes long")
    void wrongLength_isRejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AppSecurityProperties.Encryption(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("reads the key from a mounted secret file, ignoring the trailing newline")
    void keyFromSecretFile_isRead(@TempDir Path secretsDir) throws IOException {
        Files.writeString(secretsDir.resolve("ENCRYPTION_KEY"), VALID_KEY + "\n", StandardCharsets.UTF_8);

        var secrets = new ConfigTreePropertySource(
                "secrets", secretsDir, ConfigTreePropertySource.Option.AUTO_TRIM_TRAILING_NEW_LINE);
        Object value = secrets.getProperty("ENCRYPTION_KEY");

        assertThat(value).isNotNull();
        var encryption = new AppSecurityProperties.Encryption(value.toString());
        assertThat(encryption.keyBytes()).hasSize(32);
    }

    @Test
    @DisplayName("application.properties only references the key, it never contains one")
    void applicationProperties_containsNoKey() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties).contains("app.security.encryption.key=${ENCRYPTION_KEY}");
        assertThat(properties).contains("optional:configtree:");
    }
}