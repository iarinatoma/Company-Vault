package com.example.template.access.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AccessApiClient;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.integration.support.UserApiClient;
import com.github.database.rider.core.api.dataset.DataSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} via
 * {@code Class#getAnnotation}, which never sees the annotation of an enclosing class.
 */
@DataSet("datasets/access-grants.yml")
@DisplayName("Access API")
class AccessControllerIT extends AbstractIntegrationTest {

    private static final String VAULT = "VAULT";

    private AuthApiClient authApi;
    private AccessApiClient accessApi;
    private UserApiClient userApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
        accessApi = new AccessApiClient(restTemplate);
        userApi = new UserApiClient(restTemplate);
    }

    @Test
    @DisplayName("returns the caller's stored level on a resource")
    void myLevel_withGrant_returnsStoredLevel() {
        String token = authApi.accessTokenFor(TestUsers.ACCESS_WRITER_EMAIL, TestUsers.PASSWORD);

        var response = accessApi.myLevel(token, VAULT, TestUsers.ACCESS_VAULT_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("level").asString()).isEqualTo("WRITE");
        assertThat(bodyOf(response).get("resourceType").asString()).isEqualTo(VAULT);
    }

    @Test
    @DisplayName("returns NO_ACCESS for a user without a grant — someone else's vault stays closed")
    void myLevel_withoutGrant_returnsNoAccess() {
        String token = authApi.accessTokenFor(TestUsers.ACCESS_OUTSIDER_EMAIL, TestUsers.PASSWORD);

        var response = accessApi.myLevel(token, VAULT, TestUsers.ACCESS_VAULT_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("level").asString()).isEqualTo("NO_ACCESS");
    }

    @Test
    @DisplayName("returns NO_ACCESS for an auditor even with a stored grant")
    void myLevel_auditor_returnsNoAccess() {
        String token = authApi.accessTokenFor(TestUsers.ACCESS_AUDITOR_EMAIL, TestUsers.PASSWORD);

        var response = accessApi.myLevel(token, VAULT, TestUsers.ACCESS_VAULT_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("level").asString()).isEqualTo("NO_ACCESS");
    }

    @Test
    @DisplayName("returns NO_ACCESS on a resource nobody has a grant on")
    void myLevel_unknownResource_returnsNoAccess() {
        String token = authApi.accessTokenFor(TestUsers.ACCESS_WRITER_EMAIL, TestUsers.PASSWORD);

        var response = accessApi.myLevel(token, VAULT, UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("level").asString()).isEqualTo("NO_ACCESS");
    }

    @Test
    @DisplayName("answers 401 without a token")
    void myLevel_anonymous_returns401() {
        var response = accessApi.myLevel(null, VAULT, TestUsers.ACCESS_VAULT_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("deleting a user also removes their grants — the foreign key does not block the delete")
    void deleteUser_withGrants_succeeds() {
        String adminToken = authApi.accessTokenFor(TestUsers.ACCESS_ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.delete(adminToken, TestUsers.ACCESS_WRITER_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}