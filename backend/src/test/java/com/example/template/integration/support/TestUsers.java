package com.example.template.integration.support;

import java.util.UUID;

/**
 * Mirrors the YAML fixtures under {@code datasets/}. Every value here exists in exactly one
 * dataset — fixtures are per test class, never shared, so classes cannot break each other.
 * The catalog stays complete (both id and email of every row) even where a constant has no
 * caller yet, so new tests never fall back to literals.
 */
@SuppressWarnings("unused")
public final class TestUsers {

    /** Raw password behind every fixture {@code password_hash}. */
    public static final String PASSWORD = "Password123!";

    // datasets/auth-users.yml
    public static final UUID AUTH_USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final String AUTH_USER_EMAIL = "auth-user@test.local";
    public static final UUID AUTH_2FA_USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    public static final String AUTH_2FA_USER_EMAIL = "auth-2fa@test.local";
    public static final UUID AUTH_LOCKED_USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
    public static final String AUTH_LOCKED_USER_EMAIL = "auth-locked@test.local";

    // datasets/user-users.yml
    public static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    public static final String ADMIN_EMAIL = "users-admin@test.local";
    public static final UUID USER_A_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    public static final String USER_A_EMAIL = "users-a@test.local";
    public static final UUID USER_B_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000003");
    public static final String USER_B_EMAIL = "users-b@test.local";

    private TestUsers() {}
}
