package com.example.template.integration.support;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * HTTP-level access to the user endpoints with bearer authentication.
 */
public class UserApiClient {

    private final TestRestTemplate restTemplate;

    public UserApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<JsonNode> getOwnProfile(@Nullable String accessToken) {
        return exchange(HttpMethod.GET, "/api/v1/users/me", accessToken, null);
    }

    public ResponseEntity<JsonNode> getById(@Nullable String accessToken, String id) {
        return exchange(HttpMethod.GET, "/api/v1/users/" + id, accessToken, null);
    }

    public ResponseEntity<JsonNode> list(@Nullable String accessToken) {
        return exchange(HttpMethod.GET, "/api/v1/users", accessToken, null);
    }

    public ResponseEntity<JsonNode> listAtVersion(@Nullable String accessToken, String version) {
        return exchange(HttpMethod.GET, "/api/" + version + "/users", accessToken, null);
    }

    public ResponseEntity<JsonNode> create(@Nullable String accessToken, String email, String password, String role) {
        return exchange(
                HttpMethod.POST,
                "/api/v1/users",
                accessToken,
                Map.of("email", email, "password", password, "role", role));
    }

    public ResponseEntity<JsonNode> update(
            @Nullable String accessToken, String id, String email, String role, @Nullable String password) {
        var body = new java.util.HashMap<String, String>();
        body.put("email", email);
        body.put("role", role);
        if (password != null) {
            body.put("password", password);
        }
        return exchange(HttpMethod.PUT, "/api/v1/users/" + id, accessToken, body);
    }

    public ResponseEntity<JsonNode> delete(@Nullable String accessToken, String id) {
        return exchange(HttpMethod.DELETE, "/api/v1/users/" + id, accessToken, null);
    }

    private ResponseEntity<JsonNode> exchange(
            HttpMethod method, String path, @Nullable String accessToken, @Nullable Map<String, String> body) {
        var headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
