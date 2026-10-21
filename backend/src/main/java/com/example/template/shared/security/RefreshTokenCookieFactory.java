package com.example.template.shared.security;

import com.example.template.shared.config.AppSecurityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token travels only as an HttpOnly cookie, never in a request or response body.
 * {@code Secure} in every profile (localhost is exempt in browsers, so dev needs no
 * exception), path-scoped to the auth endpoints so it never rides along on ordinary API
 * calls, {@code Max-Age} equal to the refresh TTL.
 */
@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private final AppSecurityProperties properties;

    public RefreshTokenCookieFactory(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String rawToken) {
        return builder(rawToken, properties.refreshToken().ttl()).build();
    }

    public ResponseCookie clear() {
        return builder("", Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .path(properties.refreshCookie().path())
                .sameSite(properties.refreshCookie().sameSite())
                .maxAge(maxAge);
    }
}
