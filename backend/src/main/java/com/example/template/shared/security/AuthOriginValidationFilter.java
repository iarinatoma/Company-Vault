package com.example.template.shared.security;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Because {@code refresh} and {@code logout} authenticate by cookie alone, they are the one
 * place CSRF matters in this API: both validate the {@code Origin} (falling back to
 * {@code Referer}) against the same allowlist as CORS. SameSite is a mitigation, not the
 * defense. Requests with neither header (non-browser clients) pass — they carry no ambient
 * cookie credential a cross-site attacker could ride.
 */
public class AuthOriginValidationFilter extends OncePerRequestFilter {

    private static final Pattern PROTECTED_PATHS = Pattern.compile("^/api/v\\d+/auth/(refresh|logout)$");

    private final AppSecurityProperties properties;
    private final ProblemDetailResponseWriter responseWriter;

    public AuthOriginValidationFilter(AppSecurityProperties properties, ProblemDetailResponseWriter responseWriter) {
        this.properties = properties;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            origin = originOf(request.getHeader(HttpHeaders.REFERER));
        }
        if (origin != null && !properties.cors().allowedOrigins().contains(origin)) {
            responseWriter.write(response, ErrorCode.ORIGIN_NOT_ALLOWED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private @Nullable String originOf(@Nullable String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getPort() == -1
                    ? uri.getScheme() + "://" + uri.getHost()
                    : uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
