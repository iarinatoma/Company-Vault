package com.example.template.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password-reset settings. Kept next to AppSecurityProperties instead of inside it so adding
 * them does not change that record's constructor (and every test that builds it).
 */
@ConfigurationProperties(prefix = "app.security.password-reset")
public record PasswordResetProperties(Duration tokenTtl, String linkBaseUrl) {

    public PasswordResetProperties {
        if (tokenTtl.isNegative() || tokenTtl.isZero() || tokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("app.security.password-reset.token-ttl must be > 0 and <= 1 hour");
        }
        if (linkBaseUrl.isBlank()) {
            throw new IllegalArgumentException("app.security.password-reset.link-base-url must not be blank");
        }
    }
}