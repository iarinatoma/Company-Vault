package com.example.template.shared.security;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and validates the two token types ({@code access} and single-use {@code 2fa}).
 * Validation enforces {@code typ} before any other check. HS256 with a >= 256-bit secret
 * from the environment is acceptable while this service is the only verifier; the moment any
 * other service verifies these tokens, switch to EdDSA/ES256.
 *
 * <p>{@code java.util.Date} appears here only because the JJWT API requires it — this class
 * is the single sanctioned exception to the no-Date rule.
 */
@Component
public class JwtService {

    private static final String TYPE_CLAIM = "typ";
    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final AppSecurityProperties properties;

    public JwtService(AppSecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    public String mintAccessToken(UUID userId, String email, Role role) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TYPE_CLAIM, TokenType.ACCESS.claimValue())
                .claim(EMAIL_CLAIM, email)
                .claim(ROLE_CLAIM, role.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().accessTtl())))
                .signWith(key)
                .compact();
    }

    public String mintTwoFactorToken(UUID userId) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TYPE_CLAIM, TokenType.TWO_FACTOR.claimValue())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwt().twoFactorTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a token, rejecting a {@code typ} mismatch before any other check.
     * Throws {@link ApiException} with {@code INVALID_TOKEN} on any failure.
     */
    public TokenClaims parse(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(properties.jwt().clockSkew().toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        String type = claims.get(TYPE_CLAIM, String.class);
        if (!expectedType.claimValue().equals(type)) {
            // A 2fa token presented as an access token (or vice versa) is an authentication
            // bypass attempt — rejected before anything else is read.
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get(EMAIL_CLAIM, String.class);
            // Raw Map#get: 2fa tokens carry no identity claims — role (like email) is present
            // on access tokens only, so the claim's absence is a legitimate state.
            Object roleClaim = claims.get(ROLE_CLAIM);
            Role role = roleClaim == null ? null : Role.valueOf(roleClaim.toString());
            return new TokenClaims(
                    userId, email, role, claims.getId(), claims.getExpiration().toInstant());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
    }

    public record TokenClaims(
            UUID userId, @Nullable String email, @Nullable Role role, String jti, Instant expiresAt) {
    }
}
