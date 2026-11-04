package com.example.template.auth.service;

import com.example.template.auth.model.RefreshToken;
import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens are opaque 256-bit random values, stored only as SHA-256 hashes and
 * compared in constant time.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppSecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AppSecurityProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}

    /**
     * Starts a new session (login): the token expires one refresh TTL from now.
     * Participates in the caller's transaction — issuing is part of the login/refresh flow.
     */
    public IssuedRefreshToken issue(UUID userId, UUID familyId) {
        return issue(userId, familyId, Instant.now().plus(properties.refreshToken().ttl()));
    }

    /**
     * Rotation within an existing session: the new token inherits the expiry of the token it
     * replaces, so refreshing never extends a session — it ends at most one refresh TTL after
     * login (absolute session lifetime), and the user has to sign in again.
     */
    public IssuedRefreshToken issue(UUID userId, UUID familyId, Instant expiresAt) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(expiresAt);
        return new IssuedRefreshToken(rawToken, refreshTokenRepository.save(token));
    }

    public Optional<RefreshToken> find(String rawToken) {
        String hash = sha256Hex(rawToken);
        return refreshTokenRepository
                .findByTokenHash(hash)
                .filter(stored -> MessageDigest.isEqual(
                        stored.getTokenHash().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)));
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
