package com.example.template.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The access token travels in the body; the refresh token travels only as an HttpOnly
 * cookie — the {@code refreshToken} component is {@code @JsonIgnore}d so it never reaches
 * the wire, and exists only for the controller to hand to {@code RefreshTokenCookieFactory}.
 */
public record TokenPairResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        @JsonIgnore String refreshToken) implements LoginResult {}
