package com.example.template.auth.dto;

/**
 * Login succeeded but 2FA is required: the short-lived, single-use challenge token must be
 * presented together with a TOTP or recovery code.
 */
public record TwoFactorChallengeResponse(String challengeToken, long expiresInSeconds) implements LoginResult {}
