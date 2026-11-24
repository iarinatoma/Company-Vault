package com.example.template.secret.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.secret.dto.CreateSecretRequest;
import com.github.database.rider.core.api.dataset.DataSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@DataSet("datasets/secrets.yml")
@DisplayName("Secret Search and Filter over Unencrypted Metadata")
class SecretSearchAndFilterIT extends AbstractIntegrationTest {

    private static final String VAULT = TestUsers.SECRETS_VAULT_ID.toString();

    private AuthApiClient authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
    }

    private HttpHeaders authHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("searches name, description and tags in plaintext but never matches encrypted secret values")
    void searchesMetadataOnlyAndNeverMatchesEncryptedSecretValue() {
        String writer = authApi.accessTokenFor(TestUsers.SECRETS_WRITER_EMAIL, TestUsers.PASSWORD);
        String reader = authApi.accessTokenFor(TestUsers.SECRETS_READER_EMAIL, TestUsers.PASSWORD);

        var createReq = new CreateSecretRequest(
                "Payment Gateway Key",
                "top-secret-plaintext-token-98765",
                null,
                "Stripe live key for checkout service",
                List.of("Prod", "Stripe"),
                null);

        ResponseEntity<JsonNode> created = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets",
                HttpMethod.POST,
                new HttpEntity<>(createReq, authHeaders(writer)),
                JsonNode.class,
                VAULT);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secretId = bodyOf(created).get("id").asString();

        // 1. Search by description (case-insensitive)
        ResponseEntity<JsonNode> byDescription = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets?q={q}",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(reader)),
                JsonNode.class,
                VAULT,
                "CHECKOUT SERVICE");
        assertThat(byDescription.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(byDescription).size()).isEqualTo(1);
        assertThat(bodyOf(byDescription).get(0).get("id").asString()).isEqualTo(secretId);

        // 2. Filter by normalized tag (?tag=PROD matches "prod")
        ResponseEntity<JsonNode> byTag = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets?tag={tag}",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(reader)),
                JsonNode.class,
                VAULT,
                "PROD");
        assertThat(byTag.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(byTag).size()).isEqualTo(1);
        assertThat(bodyOf(byTag).get(0).get("id").asString()).isEqualTo(secretId);

        // 3. Search by text that exists ONLY in the encrypted secret value -> must return 0 results
        ResponseEntity<JsonNode> bySecretValue = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets?q={q}",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(reader)),
                JsonNode.class,
                VAULT,
                "top-secret-plaintext-token-98765");
        assertThat(bySecretValue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(bySecretValue).isEmpty()).isTrue();
    }
}