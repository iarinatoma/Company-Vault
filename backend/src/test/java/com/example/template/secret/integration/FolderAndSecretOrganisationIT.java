package com.example.template.secret.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.secret.repository.SecretRepository;
import com.github.database.rider.core.api.dataset.DataSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

@DataSet("datasets/secrets.yml")
@DisplayName("Folders, Tags and Moving Secrets API")
class FolderAndSecretOrganisationIT extends AbstractIntegrationTest {

    private static final String VAULT = TestUsers.SECRETS_VAULT_ID.toString();

    @Autowired
    private SecretRepository secretRepository;

    private AuthApiClient authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
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

    private ResponseEntity<JsonNode> post(String token, String path, Object body) {
        return restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String token, String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(String token, String path) {
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    @Test
    @DisplayName("creates folders, stores secret metadata/tags, searches by tag, moves between folders and deletes folder safely")
    void folderAndMetadataLifecycle() {
        String writer = tokenFor(TestUsers.SECRETS_WRITER_EMAIL);
        String reader = tokenFor(TestUsers.SECRETS_READER_EMAIL);
        String manager = tokenFor(TestUsers.SECRETS_MANAGER_EMAIL);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create two folders
        var f1Res = post(writer, "/api/v1/vaults/" + VAULT + "/folders", Map.of("name", "Backend-" + suffix));
        assertThat(f1Res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String folder1Id = bodyOf(f1Res).get("id").asString();

        var f2Res = post(writer, "/api/v1/vaults/" + VAULT + "/folders", Map.of("name", "DevOps-" + suffix));
        assertThat(f2Res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String folder2Id = bodyOf(f2Res).get("id").asString();

        // 2. Create secret inside Backend folder with description, tags and expiry
        String secretName = "AWS Root Key " + suffix;
        String uniqueTag = "aws-" + suffix;
        var secretRes = post(
                writer,
                "/api/v1/vaults/" + VAULT + "/secrets",
                Map.of(
                        "name", secretName,
                        "value", "AKIA-SECRET-123",
                        "folderId", folder1Id,
                        "description", "Production IAM key",
                        "tags", List.of(uniqueTag, "Prod"),
                        "expiresAt", "2027-06-01T00:00:00Z"));
        assertThat(secretRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secretId = bodyOf(secretRes).get("id").asString();
        assertThat(bodyOf(secretRes).get("folderId").asString()).isEqualTo(folder1Id);

        // 3. Search by tag as reader without decrypting
        var searchRes = get(reader, "/api/v1/vaults/" + VAULT + "/secrets?tag=" + uniqueTag);
        assertThat(searchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(searchRes)).hasSize(1);
        assertThat(bodyOf(searchRes).get(0).get("name").asString()).isEqualTo(secretName);
        assertThat(bodyOf(searchRes).toString()).doesNotContain("AKIA-SECRET-123");

        // 4. Move secret from Backend folder to DevOps folder (same vault -> ciphertext stays unchanged)
        String ciphertextBeforeMove =
                secretRepository.findById(UUID.fromString(secretId)).orElseThrow().getEncryptedValue();

        var moveRes = post(
                writer,
                "/api/v1/vaults/" + VAULT + "/secrets/" + secretId + "/move",
                Map.of("targetFolderId", folder2Id));
        assertThat(moveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(moveRes).get("folderId").asString()).isEqualTo(folder2Id);

        String ciphertextAfterMove =
                secretRepository.findById(UUID.fromString(secretId)).orElseThrow().getEncryptedValue();
        assertThat(ciphertextAfterMove).isEqualTo(ciphertextBeforeMove);

        // 5. Delete DevOps folder as manager -> secret survives at vault root (folderId = null)
        var delFolderRes = delete(manager, "/api/v1/vaults/" + VAULT + "/folders/" + folder2Id);
        assertThat(delFolderRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var rootSecrets = get(reader, "/api/v1/vaults/" + VAULT + "/secrets?rootOnly=true&q=" + suffix);
        assertThat(bodyOf(rootSecrets)).hasSize(1);
        assertThat(bodyOf(rootSecrets).get(0).get("id").asString()).isEqualTo(secretId);
    }
}