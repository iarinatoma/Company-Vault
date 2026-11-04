package com.example.template.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ProblemDetailFactory;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * The integration suite raises the limit to 10000 (every test shares one client IP), so the
 * throttling itself is verified here: the filter is instantiated directly with a small
 * capacity and driven with mock requests — no Spring context, no database.
 */
@DisplayName("AuthRateLimitFilter")
class AuthRateLimitFilterTest {

    private static final int CAPACITY = 3;
    private static final String CLIENT_IP = "203.0.113.10";
    private static final String OTHER_CLIENT_IP = "203.0.113.20";

    private static final AppSecurityProperties PROPERTIES = new AppSecurityProperties(
            new AppSecurityProperties.Jwt(
                    "unit-test-secret-with-at-least-32-bytes",
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(60)),
            new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
            new AppSecurityProperties.RefreshToken(Duration.ofDays(30), Duration.ofDays(7)),
            new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
            // Long refill period: no token comes back while a test runs.
            new AppSecurityProperties.RateLimit(CAPACITY, Duration.ofHours(1)),
            new AppSecurityProperties.Cors(List.of("https://app.example.com")),
            new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> noTracer = mock(ObjectProvider.class);
        var responseWriter = new ProblemDetailResponseWriter(
                new ProblemDetailFactory(noTracer), JsonMapper.builder().build());
        filter = new AuthRateLimitFilter(PROPERTIES, responseWriter);
    }

    @Test
    @DisplayName("lets requests through up to the capacity, then answers 429 RATE_LIMIT_EXCEEDED")
    void login_overCapacity_returns429() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            var response = post("/api/v1/auth/login", CLIENT_IP);
            assertThat(response.passedThrough()).as("request %d should pass", i + 1).isTrue();
        }

        var blocked = post("/api/v1/auth/login", CLIENT_IP);

        assertThat(blocked.passedThrough()).isFalse();
        assertThat(blocked.response().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blocked.response().getContentType()).startsWith("application/problem+json");
        assertThat(blocked.response().getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("limits every anonymous auth endpoint, including register and the password-reset flow")
    void allAuthEndpoints_areLimited() throws Exception {
        var limitedPaths = List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/2fa/verify",
                "/api/v1/auth/register",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password");

        for (String path : limitedPaths) {
            for (int i = 0; i < CAPACITY; i++) {
                post(path, CLIENT_IP);
            }
            assertThat(post(path, CLIENT_IP).response().getStatus())
                    .as("%s should be rate-limited", path)
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @Test
    @DisplayName("counts each endpoint separately: exhausting login does not block register")
    void bucketsArePerEndpoint() throws Exception {
        for (int i = 0; i <= CAPACITY; i++) {
            post("/api/v1/auth/login", CLIENT_IP);
        }

        assertThat(post("/api/v1/auth/register", CLIENT_IP).passedThrough()).isTrue();
    }

    @Test
    @DisplayName("counts each client IP separately: one attacker does not block other users")
    void bucketsArePerClientIp() throws Exception {
        for (int i = 0; i <= CAPACITY; i++) {
            post("/api/v1/auth/login", CLIENT_IP);
        }

        assertThat(post("/api/v1/auth/login", OTHER_CLIENT_IP).passedThrough()).isTrue();
    }

    @Test
    @DisplayName("does not limit endpoints outside the auth entry points")
    void otherEndpoints_areNotLimited() throws Exception {
        for (int i = 0; i < CAPACITY * 3; i++) {
            assertThat(get("/api/v1/users/me", CLIENT_IP).passedThrough()).isTrue();
        }
    }

    private Result post(String path, String clientIp) throws ServletException, IOException {
        return send("POST", path, clientIp);
    }

    private Result get(String path, String clientIp) throws ServletException, IOException {
        return send("GET", path, clientIp);
    }

    private Result send(String method, String path, String clientIp) throws ServletException, IOException {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(clientIp);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // MockFilterChain records the request once the filter hands it on.
        return new Result(chain.getRequest() != null, response);
    }

    private record Result(boolean passedThrough, MockHttpServletResponse response) {}
}