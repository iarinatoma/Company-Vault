package com.example.template.shared.security;

/**
 * The {@code typ} claim values. Token-type confusion is an authentication bypass, not an
 * edge case — every code path that parses a JWT states which type it accepts.
 */
public enum TokenType {
    ACCESS("access"),
    TWO_FACTOR("2fa");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }
}
