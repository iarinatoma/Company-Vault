package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.auth.dto.ForgotPasswordRequest;
import com.example.template.auth.dto.ResetPasswordRequest;
import com.example.template.auth.event.PasswordResetRequestedEvent;
import com.example.template.auth.model.PasswordResetToken;
import com.example.template.auth.repository.PasswordResetTokenRepository;
import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.PasswordResetProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.Role;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "someone@example.com";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String LINK_BASE_URL = "http://localhost:4200/reset-password";
    private static final String RAW_TOKEN = "some-opaque-raw-reset-token";
    private static final String NEW_PASSWORD = "a-brand-new-password-1";
    private static final PasswordResetProperties PROPERTIES = new PasswordResetProperties(TOKEN_TTL, LINK_BASE_URL);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, tokenRepository, refreshTokenRepository, passwordEncoder, PROPERTIES, eventPublisher);
    }

    private static User user() {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail(EMAIL);
        user.setPasswordHash("{bcrypt}old-hash");
        user.setRole(Role.USER);
        return user;
    }

    private static PasswordResetToken token(Instant expiresAt, Instant usedAt) {
        var token = new PasswordResetToken();
        token.setUserId(USER_ID);
        token.setTokenHash(sha256Hex(RAW_TOKEN));
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Nested
    @DisplayName("requestReset")
    class RequestResetTests {

        @Test
        @DisplayName("does nothing for an unknown e-mail — no token, no e-mail")
        void requestReset_unknownEmail_issuesNothing() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            passwordResetService.requestReset(new ForgotPasswordRequest(EMAIL));

            verify(tokenRepository, never()).save(any(PasswordResetToken.class));
            verify(eventPublisher, never()).publishEvent(any(PasswordResetRequestedEvent.class));
        }

        @Test
        @DisplayName("stores only the hash, mails a link with the raw token and invalidates older links")
        void requestReset_knownEmail_storesHashAndPublishesLink() {
            var olderToken = token(Instant.now().plus(Duration.ofMinutes(10)), null);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(tokenRepository.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(olderToken));
            var before = Instant.now();

            passwordResetService.requestReset(new ForgotPasswordRequest(EMAIL));

            var savedToken = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(savedToken.capture());
            var event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());

            String link = event.getValue().resetLink();
            assertThat(link).startsWith(LINK_BASE_URL + "#token=");
            String rawToken = link.substring((LINK_BASE_URL + "#token=").length());
            assertThat(rawToken).isNotBlank();
            assertThat(savedToken.getValue().getTokenHash()).isEqualTo(sha256Hex(rawToken));
            assertThat(savedToken.getValue().getTokenHash()).doesNotContain(rawToken);
            assertThat(savedToken.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(savedToken.getValue().getExpiresAt()).isAfterOrEqualTo(before.plus(TOKEN_TTL));
            assertThat(event.getValue().email()).isEqualTo(EMAIL);
            assertThat(olderToken.getUsedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTests {

        @Test
        @DisplayName("sets the new password, lifts a lockout, burns the token and signs out every session")
        void resetPassword_validToken_changesPasswordAndRevokesSessions() {
            var token = token(Instant.now().plus(Duration.ofMinutes(10)), null);
            var user = user();
            user.setFailedLoginAttempts(3);
            user.setLockedUntil(Instant.now().plus(Duration.ofHours(1)));
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("{bcrypt}new-hash");
            when(tokenRepository.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(token));

            passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD));

            assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new-hash");
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            assertThat(token.getUsedAt()).isNotNull();
            verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
        }

        @Test
        @DisplayName("rejects an unknown token with INVALID_TOKEN")
        void resetPassword_unknownToken_throwsInvalidToken() {
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("rejects an expired token with INVALID_TOKEN")
        void resetPassword_expiredToken_throwsInvalidToken() {
            var expired = token(Instant.now().minus(Duration.ofMinutes(1)), null);
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("rejects an already used token with INVALID_TOKEN")
        void resetPassword_usedToken_throwsInvalidToken() {
            var used = token(Instant.now().plus(Duration.ofMinutes(10)), Instant.now().minus(Duration.ofMinutes(1)));
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(used));

            assertThatThrownBy(() -> passwordResetService.resetPassword(new ResetPasswordRequest(RAW_TOKEN, NEW_PASSWORD)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}