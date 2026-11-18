package com.example.template.keys.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.github.database.rider.core.api.dataset.DataSet;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DataSet("datasets/user-users.yml")
@DisplayName("Master key backup API")
class MasterKeyBackupControllerIT extends AbstractIntegrationTest {

    private AuthApiClient authApi;

    @Value("${app.security.encryption.key}")
    private String configuredKey;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
    }

    @Test
    @DisplayName("lets an administrator download the configured key without caching it")
    void backup_admin_returnsKeyAsNoStoreAttachment() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = download(token);
        String backup = new String(response.getBody(), StandardCharsets.UTF_8).strip();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(backup).isEqualTo(configuredKey);
        assertThat(Base64.getDecoder().decode(backup)).hasSize(32);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("company-vault-master-key.txt");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("forbids a regular member from downloading the key")
    void backup_member_returnsForbidden() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        assertThat(download(token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("requires authentication")
    void backup_anonymous_returnsUnauthorized() {
        assertThat(download(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<byte[]> download(String token) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(
                "/api/v1/admin/master-key/backup",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);
    }
}
