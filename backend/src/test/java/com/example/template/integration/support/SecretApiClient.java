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
 * HTTP-level access to the secret endpoints with bearer authentication.
 */
public class SecretApiClient {

    private final TestRestTemplate restTemplate;

    public SecretApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<JsonNode> list(@Nullable String accessToken, String vaultId) {
        return exchange(HttpMethod.GET, base(vaultId), accessToken, null);
    }

    public ResponseEntity<JsonNode> reveal(@Nullable String accessToken, String vaultId, String secretId) {
        return exchange(HttpMethod.GET, base(vaultId) + "/" + secretId + "/value", accessToken, null);
    }

    public ResponseEntity<JsonNode> create(@Nullable String accessToken, String vaultId, String name, String value) {
        var body = new HashMap<String, @Nullable Object>();
        body.put("name", name);
        body.put("value", value);
        return exchange(HttpMethod.POST, base(vaultId), accessToken, body);
    }

    public ResponseEntity<JsonNode> update(
            @Nullable String accessToken, String vaultId, String secretId, String name, @Nullable String value) {
        var body = new HashMap<String, @Nullable Object>();
        body.put("name", name);
        body.put("value", value);
        return exchange(HttpMethod.PUT, base(vaultId) + "/" + secretId, accessToken, body);
    }

    public ResponseEntity<JsonNode> delete(@Nullable String accessToken, String vaultId, String secretId) {
        return exchange(HttpMethod.DELETE, base(vaultId) + "/" + secretId, accessToken, null);
    }

    private static String base(String vaultId) {
        return "/api/v1/vaults/" + vaultId + "/secrets";
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