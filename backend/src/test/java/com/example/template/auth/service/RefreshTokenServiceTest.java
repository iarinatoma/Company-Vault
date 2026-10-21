package com.example.template.auth.service;

import com.example.template.auth.model.RefreshToken;
import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String RAW_TOKEN = "some-opaque-raw-refresh-token";
    private static final String RAW_TOKEN_HASH = rawTokenSha256Hex();
    private static final AppSecurityProperties PROPERTIES = new AppSecurityProperties(
            new AppSecurityProperties.Jwt(
                    "unit-test-secret-with-at-least-32-bytes!",
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(60)),
            new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
            new AppSecurityProperties.RefreshToken(REFRESH_TTL, Duration.ofDays(7)),
            new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
            new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
            new AppSecurityProperties.Cors(List.of("https://app.example.com")),
            new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenService refreshTokenService;

    private static String rawTokenSha256Hex() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(RAW_TOKEN.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, PROPERTIES);
    }

    @Nested
    @DisplayName("issue")
    class IssueTests {

        @Test
        @DisplayName("returns a non-blank URL-safe raw token")
        void issue_ok_returnsUrlSafeRawToken() {
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            var issued = refreshTokenService.issue(USER_ID, FAMILY_ID);

            assertThat(issued.rawToken()).isNotBlank().matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("persists the entity with user, family, TTL-based expiry and a hex hash instead of the raw token")
        void issue_ok_persistsHashedEntityWithUserFamilyAndExpiry() {
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            var before = Instant.now();

            var issued = refreshTokenService.issue(USER_ID, FAMILY_ID);

            var saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            assertThat(issued.entity()).isSameAs(saved.getValue());
            assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(saved.getValue().getExpiresAt())
                    .isAfter(before)
                    .isAfter(before.plus(REFRESH_TTL).minusSeconds(5))
                    .isBefore(Instant.now().plus(REFRESH_TTL).plusSeconds(5));
            assertThat(saved.getValue().getTokenHash())
                    .matches("[0-9a-f]{64}")
                    .isNotEqualTo(issued.rawToken());
        }

        @Test
        @DisplayName("produces different raw tokens and different hashes on consecutive issues")
        void issue_calledTwice_producesDistinctTokensAndHashes() {
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            var first = refreshTokenService.issue(USER_ID, FAMILY_ID);
            var second = refreshTokenService.issue(USER_ID, FAMILY_ID);

            assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
            assertThat(second.entity().getTokenHash()).isNotEqualTo(first.entity().getTokenHash());
        }
    }

    @Nested
    @DisplayName("find")
    class FindTests {

        @Test
        @DisplayName("queries the repository with the SHA-256 hex of the raw token")
        void find_anyToken_queriesRepositoryWithSha256HexOfRawToken() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            refreshTokenService.find(RAW_TOKEN);

            var queriedHash = ArgumentCaptor.forClass(String.class);
            verify(refreshTokenRepository).findByTokenHash(queriedHash.capture());
            assertThat(queriedHash.getValue()).isEqualTo(RAW_TOKEN_HASH);
        }

        @Test
        @DisplayName("returns empty when no stored token matches the hash")
        void find_unknownToken_returnsEmpty() {
            when(refreshTokenRepository.findByTokenHash(RAW_TOKEN_HASH)).thenReturn(Optional.empty());

            var result = refreshTokenService.find(RAW_TOKEN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns the stored entity when its hash matches a previously issued raw token")
        void find_afterIssue_queriesWithStoredHashAndReturnsEntity() {
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            var issued = refreshTokenService.issue(USER_ID, FAMILY_ID);
            when(refreshTokenRepository.findByTokenHash(issued.entity().getTokenHash()))
                    .thenReturn(Optional.of(issued.entity()));

            var result = refreshTokenService.find(issued.rawToken());

            assertThat(result).containsSame(issued.entity());
            var queriedHash = ArgumentCaptor.forClass(String.class);
            verify(refreshTokenRepository).findByTokenHash(queriedHash.capture());
            assertThat(queriedHash.getValue()).isEqualTo(issued.entity().getTokenHash());
        }
    }
}
