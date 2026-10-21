package com.example.template.shared.security;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits the auth endpoints in front of the credential check, keyed by client IP and
 * requested endpoint. Complements the account lockout counter — one throttles the network,
 * the other locks the account; neither replaces the other. IP keying only works if the app
 * sees the real client IP: behind the load balancer, {@code server.forward-headers-strategy}
 * must be set and only the trusted proxy's forwarded headers honored.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern LIMITED_PATHS = Pattern.compile("^/api/v\\d+/auth/(login|refresh|2fa/verify)$");

    private final AppSecurityProperties properties;
    private final ProblemDetailResponseWriter responseWriter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppSecurityProperties properties, ProblemDetailResponseWriter responseWriter) {
        this.properties = properties;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            responseWriter.write(response, ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private Bucket newBucket() {
        var limit = properties.rateLimit();
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.refillPeriod())
                        .build())
                .build();
    }
}
