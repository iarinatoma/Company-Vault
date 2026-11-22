package com.example.template.integration.support;

import java.util.HashMap;
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
 * HTTP-level access to the vault endpoints with bearer authentication.
 */
public class VaultApiClient {

    private final TestRestTemplate restTemplate;

    public VaultApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<JsonNode> list(@Nullable String accessToken) {
        return exchange(HttpMethod.GET, "/api/v1/vaults", accessToken, null);
    }

    public ResponseEntity<JsonNode> get(@Nullable String accessToken, String id) {
        return exchange(HttpMethod.GET, "/api/v1/vaults/" + id, accessToken, null);
    }

    public ResponseEntity<JsonNode> create(
            @Nullable String accessToken, String name, @Nullable String description, String ownerId) {
        var body = new HashMap<String, @Nullable Object>();
        body.put("name", name);
        body.put("description", description);
        body.put("ownerId", ownerId);
        return exchange(HttpMethod.POST, "/api/v1/vaults", accessToken, body);
    }

    private ResponseEntity<JsonNode> exchange(
            HttpMethod method, String path, @Nullable String accessToken, @Nullable Map<String, @Nullable Object> body) {
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