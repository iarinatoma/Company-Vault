package com.example.template.shared.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All security thresholds and TTLs bind here — a hard-coded number elsewhere is a bug.
 * Invalid values are rejected at startup, not discovered in production.
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        Jwt jwt,
        RefreshCookie refreshCookie,
        RefreshToken refreshToken,
        Lockout lockout,
        RateLimit rateLimit,
        Cors cors,
        Encryption encryption) {

    public record Jwt(String secret, Duration accessTtl, Duration twoFactorTtl, Duration clockSkew) {
        public Jwt {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("app.security.jwt.secret must be at least 256 bits (32 bytes)");
            }
            if (accessTtl.compareTo(Duration.ofMinutes(15)) > 0) {
                // The access TTL is the reaction-time knob for role changes and suspensions.
                throw new IllegalArgumentException("app.security.jwt.access-ttl must be <= 15 minutes");
            }
            if (twoFactorTtl.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("app.security.jwt.two-factor-ttl must be <= 5 minutes");
            }
        }
    }

    public record RefreshCookie(String path, String sameSite) {
        private static final Set<String> ALLOWED_SAME_SITE = Set.of("Lax", "Strict", "None");

        public RefreshCookie {
            if (!ALLOWED_SAME_SITE.contains(sameSite)) {
                throw new IllegalArgumentException("app.security.refresh-cookie.same-site must be Lax, Strict or None");
            }
        }
    }

    public record RefreshToken(Duration ttl, Duration purgeGrace) {
        public RefreshToken {
            if (purgeGrace.isNegative()) {
                throw new IllegalArgumentException("app.security.refresh-token.purge-grace must not be negative");
            }
        }
    }

    public record Lockout(int maxAttempts, Duration duration) {
        public Lockout {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("app.security.lockout.max-attempts must be >= 1");
            }
        }
    }

    public record RateLimit(long capacity, Duration refillPeriod) {
        public RateLimit {
            if (capacity < 1) {
                throw new IllegalArgumentException("app.security.rate-limit.capacity must be >= 1");
            }
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            // allowCredentials=true makes a wildcard origin an account-takeover primitive.
            if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
                throw new IllegalArgumentException("app.security.cors.allowed-origins must not contain wildcards");
            }
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }

    public record Encryption(String key) {
        public Encryption {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("app.security.encryption.key must be valid base64", e);
            }
            if (decoded.length != 32) {
                throw new IllegalArgumentException("app.security.encryption.key must decode to exactly 32 bytes");
            }
        }

        public byte[] keyBytes() {
            return Base64.getDecoder().decode(key);
        }
    }
}
