package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.TokenPairResponse;
import com.example.template.auth.dto.TwoFactorChallengeResponse;
import com.example.template.auth.dto.TwoFactorVerifyRequest;
import com.example.template.auth.model.ConsumedTwoFactorToken;
import com.example.template.auth.model.RefreshToken;
import com.example.template.auth.repository.ConsumedTwoFactorTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.JwtService;
import com.example.template.shared.security.Role;
import com.example.template.shared.security.TokenType;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private SessionRevoker sessionRevoker;

    @Mock
    private TotpService totpService;

    @Mock
    private ConsumedTwoFactorTokenRepository consumedTwoFactorTokenRepository;

    private AuthService authService;

    private static final String EMAIL = "someone@example.com";
    private static final String PASSWORD = "long-password-123";
    private static final String PASSWORD_HASH = "{bcrypt}hash";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final String JTI = UUID.randomUUID().toString();
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String CHALLENGE_TOKEN = "challenge-token";

    /** A real, validated instance — AppSecurityProperties is a record with invariants, not a collaborator to mock. */
    private static final AppSecurityProperties PROPERTIES = new AppSecurityProperties(
            new AppSecurityProperties.Jwt(
                    "0123456789abcdef0123456789abcdef",
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(60)),
            new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
            new AppSecurityProperties.RefreshToken(Duration.ofDays(30), Duration.ofDays(7)),
            new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
            new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
            new AppSecurityProperties.Cors(List.of("https://app.example.com")),
            new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));

    @BeforeEach
    void setUp() {
        // Manual construction: @InjectMocks cannot supply the real (non-mock) properties record.
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                loginAttemptService,
                sessionRevoker,
                totpService,
                consumedTwoFactorTokenRepository,
                PROPERTIES);
    }

    private static User user() {
        var user = new User();
        // BaseEntity has no id setter (Hibernate generates it); persisted fixtures need reflection.
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail(EMAIL);
        user.setPasswordHash(PASSWORD_HASH);
        user.setRole(Role.USER);
        return user;
    }

    private static User lockedUser() {
        var user = user();
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        return user;
    }

    private static RefreshToken storedRefreshToken() {
        var token = new RefreshToken();
        token.setUserId(USER_ID);
        token.setTokenHash("hash-of-" + RAW_REFRESH_TOKEN);
        token.setFamilyId(FAMILY_ID);
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        return token;
    }

    private static JwtService.TokenClaims twoFactorClaims() {
        return new JwtService.TokenClaims(USER_ID, null, null, JTI, Instant.now().plus(Duration.ofMinutes(5)));
    }

    private void stubIssuedPair() {
        var issued = new RefreshTokenService.IssuedRefreshToken(RAW_REFRESH_TOKEN, new RefreshToken());
        when(refreshTokenService.issue(any(), any())).thenReturn(issued);
        when(jwtService.mintAccessToken(USER_ID, EMAIL, Role.USER)).thenReturn(ACCESS_TOKEN);
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        private final LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        @Test
        @DisplayName("throws INVALID_CREDENTIALS for an unknown email but still runs the encoder against the dummy hash")
        void login_unknownEmail_throwsInvalidCredentialsAfterDummyHashCheck() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verify(passwordEncoder).matches(eq(PASSWORD), anyString());
            verify(loginAttemptService, never()).recordFailure(any());
        }

        @Test
        @DisplayName("throws INVALID_CREDENTIALS and records the failure when the password is wrong")
        void login_wrongPassword_throwsInvalidCredentialsAndRecordsFailure() {
            var user = user();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verify(loginAttemptService).recordFailure(USER_ID);
        }

        @Test
        @DisplayName("does not record another failure when the password is wrong on an already locked account")
        void login_wrongPasswordWhileLocked_throwsInvalidCredentialsWithoutRecordingFailure() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(lockedUser()));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verify(loginAttemptService, never()).recordFailure(any());
        }

        @Test
        @DisplayName("throws INVALID_CREDENTIALS for a correct password on a locked account and never issues tokens")
        void login_correctPasswordWhileLocked_throwsInvalidCredentialsWithoutIssuingTokens() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(lockedUser()));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verify(refreshTokenService, never()).issue(any(), any());
            verify(loginAttemptService, never()).reset(any());
        }

        @Test
        @DisplayName("returns a token pair, resets the counter and upgrades the hash when 2FA is disabled")
        void login_validCredentialsWithoutTwoFactor_returnsTokenPairAndResetsCounter() {
            var user = user();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            stubIssuedPair();

            var result = authService.login(request);

            assertThat(result).isInstanceOf(TokenPairResponse.class);
            var pair = (TokenPairResponse) result;
            assertThat(pair.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(pair.tokenType()).isEqualTo("Bearer");
            assertThat(pair.expiresInSeconds())
                    .isEqualTo(PROPERTIES.jwt().accessTtl().toSeconds());
            assertThat(pair.refreshToken()).isEqualTo(RAW_REFRESH_TOKEN);
            verify(loginAttemptService).reset(USER_ID);
            verify(loginAttemptService).upgradePasswordIfNeeded(USER_ID, PASSWORD);
        }

        @Test
        @DisplayName("returns a 2FA challenge carrying the minted token and does not reset the counter yet")
        void login_validCredentialsWithTwoFactor_returnsChallengeWithoutResettingCounter() {
            var user = user();
            user.setTwoFactorEnabled(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(jwtService.mintTwoFactorToken(USER_ID)).thenReturn(CHALLENGE_TOKEN);

            var result = authService.login(request);

            assertThat(result).isInstanceOf(TwoFactorChallengeResponse.class);
            var challenge = (TwoFactorChallengeResponse) result;
            assertThat(challenge.challengeToken()).isEqualTo(CHALLENGE_TOKEN);
            assertThat(challenge.expiresInSeconds())
                    .isEqualTo(PROPERTIES.jwt().twoFactorTtl().toSeconds());
            verify(loginAttemptService, never()).reset(any());
            verify(refreshTokenService, never()).issue(any(), any());
        }
    }

    @Nested
    @DisplayName("verifyTwoFactor")
    class VerifyTwoFactorTests {

        private final TwoFactorVerifyRequest request = new TwoFactorVerifyRequest(CHALLENGE_TOKEN, "123456");

        @Test
        @DisplayName("throws INVALID_TOKEN for a replayed jti without touching the second factor")
        void verifyTwoFactor_replayedJti_throwsInvalidTokenWithoutVerification() {
            when(jwtService.parse(CHALLENGE_TOKEN, TokenType.TWO_FACTOR)).thenReturn(twoFactorClaims());
            when(consumedTwoFactorTokenRepository.existsByJti(JTI)).thenReturn(true);

            assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
            verifyNoInteractions(totpService);
            verify(consumedTwoFactorTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws INVALID_TOKEN when the token's user no longer exists")
        void verifyTwoFactor_unknownUser_throwsInvalidToken() {
            when(jwtService.parse(CHALLENGE_TOKEN, TokenType.TWO_FACTOR)).thenReturn(twoFactorClaims());
            when(consumedTwoFactorTokenRepository.existsByJti(JTI)).thenReturn(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(consumedTwoFactorTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws INVALID_CREDENTIALS when the account is locked")
        void verifyTwoFactor_lockedUser_throwsInvalidCredentials() {
            when(jwtService.parse(CHALLENGE_TOKEN, TokenType.TWO_FACTOR)).thenReturn(twoFactorClaims());
            when(consumedTwoFactorTokenRepository.existsByJti(JTI)).thenReturn(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(lockedUser()));

            assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verifyNoInteractions(totpService);
            verify(consumedTwoFactorTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws INVALID_CREDENTIALS, records the failure and never consumes the jti on a wrong code")
        void verifyTwoFactor_wrongCode_throwsInvalidCredentialsAndRecordsFailure() {
            var user = user();
            when(jwtService.parse(CHALLENGE_TOKEN, TokenType.TWO_FACTOR)).thenReturn(twoFactorClaims());
            when(consumedTwoFactorTokenRepository.existsByJti(JTI)).thenReturn(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(totpService.verify(user, "123456")).thenReturn(false);

            assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verify(loginAttemptService).recordFailure(USER_ID);
            verify(consumedTwoFactorTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("consumes the jti, resets the counter and returns a token pair on a valid code")
        void verifyTwoFactor_validCode_consumesJtiAndReturnsTokenPair() {
            var user = user();
            when(jwtService.parse(CHALLENGE_TOKEN, TokenType.TWO_FACTOR)).thenReturn(twoFactorClaims());
            when(consumedTwoFactorTokenRepository.existsByJti(JTI)).thenReturn(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(totpService.verify(user, "123456")).thenReturn(true);
            stubIssuedPair();

            var result = authService.verifyTwoFactor(request);

            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(RAW_REFRESH_TOKEN);
            var consumed = ArgumentCaptor.forClass(ConsumedTwoFactorToken.class);
            verify(consumedTwoFactorTokenRepository).save(consumed.capture());
            assertThat(consumed.getValue().getJti()).isEqualTo(JTI);
            verify(loginAttemptService).reset(USER_ID);
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("throws INVALID_REFRESH_TOKEN for a null or blank cookie value without a lookup")
        void refresh_nullOrBlankToken_throwsInvalidRefreshTokenWithoutLookup() {
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            assertThatThrownBy(() -> authService.refresh("  "))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        @DisplayName("throws INVALID_REFRESH_TOKEN when the token is unknown")
        void refresh_unknownToken_throwsInvalidRefreshToken() {
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verify(refreshTokenService, never()).issue(any(), any());
        }

        @Test
        @DisplayName("revokes the whole session family when a rotated token is replayed")
        void refresh_revokedToken_revokesFamilyAndThrowsInvalidRefreshToken() {
            var stored = storedRefreshToken();
            stored.setRevokedAt(Instant.now().minus(Duration.ofMinutes(1)));
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verify(sessionRevoker).revokeFamily(FAMILY_ID);
            verify(refreshTokenService, never()).issue(any(), any());
        }

        @Test
        @DisplayName("throws INVALID_REFRESH_TOKEN for an expired token without revoking the family")
        void refresh_expiredToken_throwsInvalidRefreshTokenWithoutFamilyRevocation() {
            var stored = storedRefreshToken();
            stored.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verify(sessionRevoker, never()).revokeFamily(any());
            verify(refreshTokenService, never()).issue(any(), any());
        }

        @Test
        @DisplayName("throws INVALID_REFRESH_TOKEN when the token's user is locked")
        void refresh_lockedUser_throwsInvalidRefreshToken() {
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.of(storedRefreshToken()));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(lockedUser()));

            assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verify(refreshTokenService, never()).issue(any(), any());
        }

        @Test
        @DisplayName("rotates the old token and issues a new pair within the same family")
        void refresh_validToken_rotatesOldTokenAndIssuesPairInSameFamily() {
            var stored = storedRefreshToken();
            var user = user();
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.of(stored));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            stubIssuedPair();

            var result = authService.refresh(RAW_REFRESH_TOKEN);

            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(RAW_REFRESH_TOKEN);
            assertThat(stored.getRevokedAt()).isNotNull();
            verify(refreshTokenService).issue(USER_ID, FAMILY_ID);
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("does nothing for a null or blank cookie value")
        void logout_nullOrBlankToken_doesNothing() {
            authService.logout(null);
            authService.logout("  ");

            verifyNoInteractions(refreshTokenService);
        }

        @Test
        @DisplayName("revokes the stored refresh token")
        void logout_knownToken_revokesStoredToken() {
            var stored = storedRefreshToken();
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.of(stored));

            authService.logout(RAW_REFRESH_TOKEN);

            assertThat(stored.getRevokedAt()).isNotNull();
        }

        @Test
        @DisplayName("stays silent for an unknown token")
        void logout_unknownToken_throwsNothing() {
            when(refreshTokenService.find(RAW_REFRESH_TOKEN)).thenReturn(Optional.empty());

            assertThatCode(() -> authService.logout(RAW_REFRESH_TOKEN)).doesNotThrowAnyException();
        }
    }
}
