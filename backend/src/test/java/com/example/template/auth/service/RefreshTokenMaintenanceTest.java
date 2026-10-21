package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenMaintenance")
class RefreshTokenMaintenanceTest {

    private static final Duration PURGE_GRACE = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenMaintenance refreshTokenMaintenance;

    private static final AppSecurityProperties PROPERTIES = new AppSecurityProperties(
            new AppSecurityProperties.Jwt(
                    "unit-test-secret-with-at-least-32-bytes",
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(60)),
            new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
            new AppSecurityProperties.RefreshToken(Duration.ofDays(30), PURGE_GRACE),
            new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
            new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
            new AppSecurityProperties.Cors(List.of("https://app.example.com")),
            new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));

    @BeforeEach
    void setUp() {
        refreshTokenMaintenance = new RefreshTokenMaintenance(refreshTokenRepository, PROPERTIES);
    }

    @Nested
    @DisplayName("purgeExpired")
    class PurgeExpiredTests {

        @Test
        @DisplayName("deletes rows expired longer ago than the purge grace")
        void purgeExpired_graceConfigured_deletesWithCutoffBeforeNow() {
            when(refreshTokenRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(3);
            var before = Instant.now().minus(PURGE_GRACE);

            refreshTokenMaintenance.purgeExpired();

            var after = Instant.now().minus(PURGE_GRACE);
            var cutoff = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).deleteAllExpiredBefore(cutoff.capture());
            assertThat(cutoff.getValue()).isBetween(before, after);
        }

        @Test
        @DisplayName("completes quietly when nothing is old enough to purge")
        void purgeExpired_nothingExpired_completesWithoutError() {
            when(refreshTokenRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(0);

            refreshTokenMaintenance.purgeExpired();

            verify(refreshTokenRepository).deleteAllExpiredBefore(any(Instant.class));
        }
    }
}
