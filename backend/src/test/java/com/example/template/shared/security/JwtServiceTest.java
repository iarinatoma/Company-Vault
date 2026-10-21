package com.example.template.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JwtService is pure crypto with no collaborators, so — like a mapper test — nothing is
 * mocked: a real {@link AppSecurityProperties} (passing its compact-constructor validation)
 * feeds a directly instantiated service.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
    private static final String OTHER_SECRET = "different-unit-test-jwt-secret-fedcba98";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(5);
    private static final Duration TWO_FACTOR_TTL = Duration.ofMinutes(2);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "someone@example.com";
    private static final Role ROLE = Role.USER;

    private final JwtService jwtService = new JwtService(properties(SECRET, TWO_FACTOR_TTL, CLOCK_SKEW));

    private static AppSecurityProperties properties(String secret, Duration twoFactorTtl, Duration clockSkew) {
        return new AppSecurityProperties(
                new AppSecurityProperties.Jwt(secret, ACCESS_TTL, twoFactorTtl, clockSkew),
                new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
                new AppSecurityProperties.RefreshToken(Duration.ofDays(14), Duration.ofDays(7)),
                new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
                new AppSecurityProperties.Cors(List.of("https://app.example.com")),
                new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Nested
    @DisplayName("mintAccessToken")
    class MintAccessTokenTests {

        @Test
        @DisplayName("mints a token that parses back as ACCESS carrying userId, email, role, jti and ~accessTtl expiry")
        void mintAccessToken_validInputs_parsesBackWithAllClaims() {
            var before = Instant.now();

            var token = jwtService.mintAccessToken(USER_ID, EMAIL, ROLE);
            var claims = jwtService.parse(token, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(USER_ID);
            assertThat(claims.email()).isEqualTo(EMAIL);
            assertThat(claims.role()).isEqualTo(ROLE);
            assertThat(claims.jti()).isNotNull();
            // The exp claim is second-granular, so allow a small tolerance around now + accessTtl.
            assertThat(claims.expiresAt())
                    .isAfter(Instant.now())
                    .isAfter(before.plus(ACCESS_TTL).minusSeconds(2))
                    .isBefore(Instant.now().plus(ACCESS_TTL).plusSeconds(2));
        }

        @Test
        @DisplayName("mints a unique jti for every token")
        void mintAccessToken_calledTwice_mintsDistinctJtis() {
            var firstToken = jwtService.mintAccessToken(USER_ID, EMAIL, ROLE);
            var secondToken = jwtService.mintAccessToken(USER_ID, EMAIL, ROLE);

            var firstJti = jwtService.parse(firstToken, TokenType.ACCESS).jti();
            var secondJti = jwtService.parse(secondToken, TokenType.ACCESS).jti();

            assertThat(firstJti).isNotEqualTo(secondJti);
        }
    }

    @Nested
    @DisplayName("mintTwoFactorToken")
    class MintTwoFactorTokenTests {

        @Test
        @DisplayName("mints a token that parses back as TWO_FACTOR carrying userId and jti but no email or role")
        void mintTwoFactorToken_validInput_parsesBackWithoutIdentityClaims() {
            var before = Instant.now();

            var token = jwtService.mintTwoFactorToken(USER_ID);
            var claims = jwtService.parse(token, TokenType.TWO_FACTOR);

            assertThat(claims.userId()).isEqualTo(USER_ID);
            assertThat(claims.email()).isNull();
            assertThat(claims.role()).isNull();
            assertThat(claims.jti()).isNotNull();
            assertThat(claims.expiresAt())
                    .isAfter(before.plus(TWO_FACTOR_TTL).minusSeconds(2))
                    .isBefore(Instant.now().plus(TWO_FACTOR_TTL).plusSeconds(2));
        }
    }

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("rejects an access token presented as a 2FA token with INVALID_TOKEN")
        void parse_accessTokenPresentedAsTwoFactor_throwsInvalidToken() {
            var token = jwtService.mintAccessToken(USER_ID, EMAIL, ROLE);

            assertThatThrownBy(() -> jwtService.parse(token, TokenType.TWO_FACTOR))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("rejects a 2FA token presented as an access token with INVALID_TOKEN")
        void parse_twoFactorTokenPresentedAsAccess_throwsInvalidToken() {
            var token = jwtService.mintTwoFactorToken(USER_ID);

            assertThatThrownBy(() -> jwtService.parse(token, TokenType.ACCESS))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("rejects a garbage string with INVALID_TOKEN")
        void parse_garbageString_throwsInvalidToken() {
            assertThatThrownBy(() -> jwtService.parse("not-a-jwt-at-all", TokenType.ACCESS))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("rejects a token signed with a different secret with INVALID_TOKEN")
        void parse_tokenSignedWithDifferentSecret_throwsInvalidToken() {
            var foreignService = new JwtService(properties(OTHER_SECRET, TWO_FACTOR_TTL, CLOCK_SKEW));
            var foreignToken = foreignService.mintAccessToken(USER_ID, EMAIL, ROLE);

            assertThatThrownBy(() -> jwtService.parse(foreignToken, TokenType.ACCESS))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("rejects an expired token with INVALID_TOKEN")
        void parse_expiredToken_throwsInvalidToken() {
            // The Jwt compact constructor only enforces the <= 5 minute ceiling, so a negative
            // twoFactorTtl is valid config-wise and mints a token that is already expired —
            // deterministic expiry coverage with zero clock skew and no sleeping.
            var expiringService = new JwtService(properties(SECRET, Duration.ofSeconds(-120), Duration.ZERO));
            var expiredToken = expiringService.mintTwoFactorToken(USER_ID);

            assertThatThrownBy(() -> expiringService.parse(expiredToken, TokenType.TWO_FACTOR))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }
    }
}
