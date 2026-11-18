package com.example.template.integration.support;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * HTTP-level access to the access-level endpoints with bearer authentication.
 */
public class AccessApiClient {

    private final TestRestTemplate restTemplate;

    public AccessApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<JsonNode> myLevel(@Nullable String accessToken, String resourceType, String resourceId) {
        var headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return restTemplate.exchange(
                "/api/v1/access/" + resourceType + "/" + resourceId + "/me",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                JsonNode.class);
    }
}