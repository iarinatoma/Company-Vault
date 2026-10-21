package com.example.template.shared.security;

/**
 * The closed set of roles. Every switch over this enum (ScopeAccessGuard, LoginResult
 * handling) is exhaustive on purpose — adding a role must fail compilation in every guard.
 */
public enum Role {
    ADMIN,
    MANAGER,
    USER
}
