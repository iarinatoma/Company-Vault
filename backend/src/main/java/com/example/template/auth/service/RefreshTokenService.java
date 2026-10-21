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

    /** Participates in the caller's transaction — issuing is part of the login/refresh flow. */
    public IssuedRefreshToken issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(properties.refreshToken().ttl()));
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
