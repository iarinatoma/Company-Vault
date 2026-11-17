package com.example.template.keys.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.shared.config.AppSecurityProperties;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MasterKeyBackupServiceTest {

    @Test
    @DisplayName("exports the configured key in ENCRYPTION_KEY-compatible Base64")
    void backupValue_encodesConfiguredKey() {
        String configuredKey = Base64.getEncoder().encodeToString(new byte[32]);
        var properties = new AppSecurityProperties(
                new AppSecurityProperties.Jwt(
                        "j".repeat(32),
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(60)),
                new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
                new AppSecurityProperties.RefreshToken(Duration.ofHours(8), Duration.ofDays(7)),
                new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
                new AppSecurityProperties.Cors(List.of("http://localhost:4200")),
                new AppSecurityProperties.Encryption(configuredKey));
        var service = new MasterKeyBackupService(properties);

        assertThat(service.backupValue(UUID.randomUUID())).isEqualTo(configuredKey);
    }
}
