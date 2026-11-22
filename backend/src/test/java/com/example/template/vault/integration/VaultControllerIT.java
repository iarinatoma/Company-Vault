package com.example.template.vault.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.integration.support.VaultApiClient;
import com.github.database.rider.core.api.dataset.DataSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} via
 * {@code Class#getAnnotation}, which never sees the annotation of an enclosing class.
 */
@DataSet("datasets/vaults.yml")
@DisplayName("Vault API")
class VaultControllerIT extends AbstractIntegrationTest {

    private AuthApiClient authApi;
    private VaultApiClient vaultApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
        vaultApi = new VaultApiClient(restTemplate);
    }

    private String tokenFor(String email) {
        return authApi.accessTokenFor(email, TestUsers.PASSWORD);
    }

    private static List<String> names(JsonNode vaults) {
        var names = new ArrayList<String>();
        vaults.forEach(vault -> names.add(vault.get("name").asString()));
        return names;
    }

    @Test
    @DisplayName("an admin creates a general vault; the owner gets MANAGE, the admin gets no access")
    void create_asAdmin_returns201AndOwnerManages() {
        var response = vaultApi.create(
                tokenFor(TestUsers.VAULTS_ADMIN_EMAIL), "IT Infrastructure", "Servers", TestUsers.VAULTS_MEMBER_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bodyOf(response).get("type").asString()).isEqualTo("GENERAL");
        assertThat(bodyOf(response).get("accessLevel").asString()).isEqualTo("NO_ACCESS");

        String id = bodyOf(response).get("id").asString();
        var asOwner = vaultApi.get(tokenFor(TestUsers.VAULTS_MEMBER_EMAIL), id);
        assertThat(asOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(asOwner).get("accessLevel").asString()).isEqualTo("MANAGE");
    }

    @Test
    @DisplayName("a member cannot create a vault")
    void create_asMember_returns403() {
        var response = vaultApi.create(
                tokenFor(TestUsers.VAULTS_MEMBER_EMAIL), "Mine", null, TestUsers.VAULTS_MEMBER_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a blank name is rejected with 400 VALIDATION_FAILED")
    void create_blankName_returns400() {
        var response = vaultApi.create(
                tokenFor(TestUsers.VAULTS_ADMIN_EMAIL), " ", null, TestUsers.VAULTS_MEMBER_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("an unknown owner is rejected with 404 USER_NOT_FOUND")
    void create_unknownOwner_returns404() {
        var response = vaultApi.create(
                tokenFor(TestUsers.VAULTS_ADMIN_EMAIL), "Ghost", null, UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("an admin lists every active vault, archived ones are hidden")
    void list_asAdmin_returnsAllActiveVaults() {
        var response = vaultApi.list(tokenFor(TestUsers.VAULTS_ADMIN_EMAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(bodyOf(response))).containsExactly("Shared Accounts", "Wi-Fi & Office");
    }

    @Test
    @DisplayName("a member lists only the vaults they have access to, with their level")
    void list_asMember_returnsOnlyOwnVaults() {
        var response = vaultApi.list(tokenFor(TestUsers.VAULTS_MEMBER_EMAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(bodyOf(response))).containsExactly("Shared Accounts");
        assertThat(bodyOf(response).get(0).get("accessLevel").asString()).isEqualTo("WRITE");
    }

    @Test
    @DisplayName("a user without grants sees no vaults and cannot open one")
    void outsider_seesNothing() {
        String token = tokenFor(TestUsers.VAULTS_OUTSIDER_EMAIL);

        assertThat(bodyOf(vaultApi.list(token)).size()).isZero();

        var response = vaultApi.get(token, TestUsers.VAULTS_SHARED_ID.toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("FORBIDDEN");
    }
}