package com.example.template.shared.security;

import java.util.UUID;

/**
 * The authenticated caller, built entirely from the access token — no DB lookup. A role
 * change or suspension therefore only takes effect at access-token expiry; the short access
 * TTL is the reaction-time bound.
 */
public record AuthPrincipal(UUID userId, String email, Role role) {}
