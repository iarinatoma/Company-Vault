package com.example.template.secret.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.SecretApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.secret.repository.SecretRepository;
import com.github.database.rider.core.api.dataset.DataSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} via
 * {@code Class#getAnnotation}, which never sees the annotation of an enclosing class.
 */
@DataSet("datasets/secrets.yml")
@DisplayName("Secret API")
class SecretControllerIT extends AbstractIntegrationTest {

    private static final String VAULT = TestUsers.SECRETS_VAULT_ID.toString();
    private static final String SECRET_VALUE = "correct-horse-battery-staple";

    @Autowired
    private SecretRepository secretRepository;

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

    /** Creates a secret as the writer and returns its id. */
    private String createdSecret(String name) {
        var response = secretApi.create(tokenFor(TestUsers.SECRETS_WRITER_EMAIL), VAULT, name, SECRET_VALUE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return bodyOf(response).get("id").asString();
    }

    @Test
    @DisplayName("a writer creates a secret; it is stored encrypted and revealed only on request")
    void create_storesEncryptedValue_andRevealsIt() {
        String id = createdSecret("Database");

        var stored = secretRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getEncryptedValue()).isNotEqualTo(SECRET_VALUE).doesNotContain(SECRET_VALUE);

        var revealed = secretApi.reveal(tokenFor(TestUsers.SECRETS_READER_EMAIL), VAULT, id);
        assertThat(revealed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(revealed).get("value").asString()).isEqualTo(SECRET_VALUE);
    }

    @Test
    @DisplayName("the list shows metadata and the owner, never the value")
    void list_returnsMetadataWithoutValue() {
        createdSecret("API key");

        var response = secretApi.list(tokenFor(TestUsers.SECRETS_READER_EMAIL), VAULT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var first = bodyOf(response).get(0);
        assertThat(first.get("name").asString()).isEqualTo("API key");
        assertThat(first.get("ownerEmail").asString()).isEqualTo(TestUsers.SECRETS_WRITER_EMAIL);
        assertThat(first.has("value")).isFalse();
        assertThat(response.getBody().toString()).doesNotContain(SECRET_VALUE);
    }

    @Test
    @DisplayName("a reader cannot create secrets")
    void create_asReader_returns403() {
        var response = secretApi.create(tokenFor(TestUsers.SECRETS_READER_EMAIL), VAULT, "X", "v");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("security: someone without access can neither list, reveal nor create")
    void outsider_isBlockedEverywhere() {
        String id = createdSecret("Server");
        String outsider = tokenFor(TestUsers.SECRETS_OUTSIDER_EMAIL);

        assertThat(secretApi.list(outsider, VAULT).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(secretApi.reveal(outsider, VAULT, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(secretApi.create(outsider, VAULT, "X", "v").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an auditor never sees secret values, even with a grant")
    void auditor_cannotReveal() {
        String id = createdSecret("Server");

        var response = secretApi.reveal(tokenFor(TestUsers.SECRETS_AUDITOR_EMAIL), VAULT, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a writer cannot delete; a manager can, and the secret is gone")
    void delete_needsManage() {
        String id = createdSecret("Temp");

        assertThat(secretApi.delete(tokenFor(TestUsers.SECRETS_WRITER_EMAIL), VAULT, id).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String manager = tokenFor(TestUsers.SECRETS_MANAGER_EMAIL);
        assertThat(secretApi.delete(manager, VAULT, id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(secretApi.reveal(manager, VAULT, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("editing without a value keeps it; with a value replaces it")
    void update_keepsOrReplacesValue() {
        String id = createdSecret("Database");
        String writer = tokenFor(TestUsers.SECRETS_WRITER_EMAIL);

        assertThat(secretApi.update(writer, VAULT, id, "Database (prod)", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(secretApi.reveal(writer, VAULT, id)).get("value").asString()).isEqualTo(SECRET_VALUE);

        secretApi.update(writer, VAULT, id, "Database (prod)", "n3w-value");
        assertThat(bodyOf(secretApi.reveal(writer, VAULT, id)).get("value").asString()).isEqualTo("n3w-value");
    }

    @Test
    @DisplayName("an archived vault is read-only (409 VAULT_ARCHIVED)")
    void create_inArchivedVault_returns409() {
        var response = secretApi.create(
                tokenFor(TestUsers.SECRETS_WRITER_EMAIL), TestUsers.SECRETS_ARCHIVED_VAULT_ID.toString(), "X", "v");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("VAULT_ARCHIVED");
    }

    @Test
    @DisplayName("a secret cannot be reached through another vault (404)")
    void secretOfOtherVault_isNotFound() {
        String id = createdSecret("Database");

        var response = secretApi.reveal(
                tokenFor(TestUsers.SECRETS_WRITER_EMAIL), TestUsers.SECRETS_ARCHIVED_VAULT_ID.toString(), id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}