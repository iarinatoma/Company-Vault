package com.example.template.auth.service;

import com.example.template.auth.dto.ForgotPasswordRequest;
import com.example.template.auth.dto.ResetPasswordRequest;
import com.example.template.auth.event.PasswordResetRequestedEvent;
import com.example.template.auth.model.PasswordResetToken;
import com.example.template.auth.repository.PasswordResetTokenRepository;
import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.PasswordResetProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot-password flow. Requesting a reset answers identically whether or not the e-mail
 * belongs to an account, so the endpoint cannot be used to find out who has one. Tokens are
 * opaque 256-bit random values, stored only as SHA-256 hashes, single-use and short-lived;
 * only the newest link of a user works. A successful reset signs the user out everywhere
 * and lifts a lockout.
 */
@Service
public class PasswordResetService {

    // Log safelist: identifiers and outcomes only — never tokens, links or passwords.
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetProperties properties,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        var user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            // Same outcome for the caller as a known e-mail.
            log.info("Password reset requested for unknown identifier {}", request.email());
            return;
        }
        var now = Instant.now();
        invalidateOpenTokens(user.requireId(), now);

        String rawToken = newRawToken();
        var token = new PasswordResetToken();
        token.setUserId(user.requireId());
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(now.plus(properties.tokenTtl()));
        tokenRepository.save(token);

        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                user.requireId(), user.getEmail(), resetLink(rawToken), token.getExpiresAt()));
        log.info("Password reset link issued for user {}", user.requireId());
    }

    /**
     * Two concurrent redemptions of the same token cannot both succeed: the token row is
     * version-checked, so the second commit fails as a concurrent modification.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        var token = tokenRepository
                .findByTokenHash(sha256Hex(request.token()))
                .orElseThrow(() -> {
                    log.warn("Password reset rejected: unknown token (INVALID_TOKEN)");
                    return new ApiException(ErrorCode.INVALID_TOKEN);
                });
        var now = Instant.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            log.warn("Password reset rejected for user {}: token used or expired (INVALID_TOKEN)", token.getUserId());
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        var user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Proving control of the mailbox also lifts a lockout.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        invalidateOpenTokens(user.requireId(), now);
        // Whoever knew the old password must not stay signed in. Access tokens already
        // issued stay valid until they expire (<= 15 minutes) — the same trade-off as logout.
        int sessions = refreshTokenRepository.deleteAllByUserId(user.requireId());
        log.info("Password reset for user {}; {} sessions signed out", user.requireId(), sessions);
    }

    private void invalidateOpenTokens(UUID userId, Instant now) {
        tokenRepository.findAllByUserIdAndUsedAtIsNull(userId).forEach(token -> token.setUsedAt(now));
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Fragment, not query string: the browser never sends it to a server or a proxy log. */
    private String resetLink(String rawToken) {
        return properties.linkBaseUrl() + "#token=" + rawToken;
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}