package com.example.template.secret.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.SecretApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.UpdateSecretRequest;
import com.github.database.rider.core.api.dataset.DataSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@DisplayName("Secret Metadata API")
class SecretMetadataIT extends AbstractIntegrationTest {

    private static final String VAULT = TestUsers.SECRETS_VAULT_ID.toString();

    private AuthApiClient authApi;
    private SecretApiClient secretApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
        secretApi = new SecretApiClient(restTemplate);
    }

    private String tokenFor(String email) {
        return authApi.accessTokenFor(email, TestUsers.PASSWORD);
    }

    private HttpHeaders authHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("creates, updates and filters secrets by metadata without exposing the value")
    void createsUpdatesAndFiltersSecretsByMetadataWithoutExposingValue() {
        String writer = tokenFor(TestUsers.SECRETS_WRITER_EMAIL);
        String reader = tokenFor(TestUsers.SECRETS_READER_EMAIL);

        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var createReq = new CreateSecretRequest(
                "AWS Production Key",
                "super-secret-value",
                "Primary IAM key for production workloads",
                List.of("Prod", "AWS", " prod "),
                expiry);

        ResponseEntity<JsonNode> created = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets",
                HttpMethod.POST,
                new HttpEntity<>(createReq, authHeaders(writer)),
                JsonNode.class,
                VAULT);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = bodyOf(created);
        assertThat(createdBody.get("name").asString()).isEqualTo("AWS Production Key");
        assertThat(createdBody.get("description").asString()).isEqualTo("Primary IAM key for production workloads");
        assertThat(createdBody.get("tags").get(0).asString()).isEqualTo("prod");
        assertThat(createdBody.get("tags").get(1).asString()).isEqualTo("aws");
        assertThat(createdBody.get("expiresAt").asString()).isEqualTo(expiry.toString());
        assertThat(createdBody.get("ownerEmail").asString()).isEqualTo(TestUsers.SECRETS_WRITER_EMAIL);
        assertThat(createdBody.get("createdAt").asString()).isNotBlank();
        assertThat(createdBody.get("updatedAt").asString()).isNotBlank();
        assertThat(createdBody.toString()).doesNotContain("super-secret-value");

        String secretId = createdBody.get("id").asString();

        // Filter by normalized tag and query without decrypting
        ResponseEntity<JsonNode> filtered = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets?tag={tag}&q={q}",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(reader)),
                JsonNode.class,
                VAULT,
                "PROD",
                "aws");

        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listBody = bodyOf(filtered);
        assertThat(listBody.size()).isEqualTo(1);
        assertThat(listBody.get(0).get("id").asString()).isEqualTo(secretId);

        // Update metadata only (value = null preserves encrypted value)
        var updateReq = new UpdateSecretRequest(
                "AWS Production Key",
                null,
                "Updated description",
                List.of("staging"),
                null);

        ResponseEntity<JsonNode> updated = restTemplate.exchange(
                "/api/v1/vaults/{vaultId}/secrets/{secretId}",
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, authHeaders(writer)),
                JsonNode.class,
                VAULT,
                secretId);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedBody = bodyOf(updated);
        assertThat(updatedBody.get("description").asString()).isEqualTo("Updated description");
        assertThat(updatedBody.get("tags").get(0).asString()).isEqualTo("staging");
        assertThat(updatedBody.get("expiresAt").isNull()).isTrue();

        // Original encrypted value is still intact
        var revealed = secretApi.reveal(reader, VAULT, secretId);
        assertThat(revealed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(revealed).get("value").asString()).isEqualTo("super-secret-value");
    }
}