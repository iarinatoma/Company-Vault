package com.example.template.integration.support;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * HTTP-level access to the auth endpoints. Cookies are handled explicitly — the tests assert
 * on the raw Set-Cookie contract, so no cookie jar.
 */
public class AuthApiClient {

    private final TestRestTemplate restTemplate;

    public AuthApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static HttpHeaders refreshHeaders(@Nullable String refreshCookie, @Nullable String origin) {
        var headers = new HttpHeaders();
        if (refreshCookie != null) {
            headers.add(HttpHeaders.COOKIE, refreshCookie);
        }
        if (origin != null) {
            headers.setOrigin(origin);
        }
        return headers;
    }

    /**
     * Extracts the {@code refresh_token=...} cookie pair from a response, if present.
     */
    public static @Nullable String refreshCookieOf(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        return cookies.stream()
                .filter(cookie -> cookie.startsWith("refresh_token="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    /**
     * The full Set-Cookie header for the refresh token, attributes included.
     */
    public static @Nullable String refreshSetCookieHeaderOf(ResponseEntity<?> response) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("refresh_token="))
                .findFirst()
                .orElse(null);
    }

    private static HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    public ResponseEntity<JsonNode> login(String email, String password) {
        return restTemplate.postForEntity(
                "/api/v1/auth/login", jsonEntity(Map.of("email", email, "password", password)), JsonNode.class);
    }

    public ResponseEntity<JsonNode> verifyTwoFactor(String challengeToken, String code) {
        return restTemplate.postForEntity(
                "/api/v1/auth/2fa/verify",
                jsonEntity(Map.of("challengeToken", challengeToken, "code", code)),
                JsonNode.class);
    }

    public ResponseEntity<JsonNode> refresh(@Nullable String refreshCookie, @Nullable String origin) {
        return restTemplate.postForEntity(
                "/api/v1/auth/refresh", new HttpEntity<>(null, refreshHeaders(refreshCookie, origin)), JsonNode.class);
    }

    /**
     * Same call without body deserialization — for responses answered by the CORS filter,
     * which carry no JSON body (e.g. a disallowed Origin).
     */
    public ResponseEntity<String> refreshRaw(@Nullable String refreshCookie, @Nullable String origin) {
        return restTemplate.postForEntity(
                "/api/v1/auth/refresh", new HttpEntity<>(null, refreshHeaders(refreshCookie, origin)), String.class);
    }

    public ResponseEntity<JsonNode> logout(@Nullable String refreshCookie) {
        var headers = new HttpHeaders();
        if (refreshCookie != null) {
            headers.add(HttpHeaders.COOKIE, refreshCookie);
        }
        return restTemplate.postForEntity("/api/v1/auth/logout", new HttpEntity<>(null, headers), JsonNode.class);
    }

    /**
     * Convenience for tests that just need a valid access token.
     */
    public String accessTokenFor(String email, String password) {
        var response = login(email, password);
        JsonNode body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || !body.hasNonNull("accessToken")) {
            throw new IllegalStateException("Login for " + email + " did not return an access token: " + response);
        }
        return body.get("accessToken").asString();
    }
}
