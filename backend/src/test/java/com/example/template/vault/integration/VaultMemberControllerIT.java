package com.example.template.vault.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.integration.support.VaultApiClient;
import com.github.database.rider.core.api.dataset.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} via
 * {@code Class#getAnnotation}, which never sees the annotation of an enclosing class.
 */
@DataSet("datasets/vault-members.yml")
@DisplayName("Vault members API")
class VaultMemberControllerIT extends AbstractIntegrationTest {

    private static final String VAULT = TestUsers.MEMBERS_VAULT_ID.toString();

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

    @Test
    @DisplayName("a project owner creates a project vault and manages it")
    void createProject_asProjectOwner_returns201WithManage() {
        var response = vaultApi.createProject(tokenFor(TestUsers.MEMBERS_PO_EMAIL), "Mobile App", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bodyOf(response).get("type").asString()).isEqualTo("PROJECT");
        assertThat(bodyOf(response).get("accessLevel").asString()).isEqualTo("MANAGE");
    }

    @Test
    @DisplayName("a member cannot create a project vault")
    void createProject_asMember_returns403() {
        var response = vaultApi.createProject(tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL), "Mine", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a manager adds a colleague, who then sees the vault with that level")
    void addMember_colleagueSeesVault() {
        var added = vaultApi.addMember(
                tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL), VAULT, TestUsers.MEMBERS_COLLEAGUE_EMAIL, "READ");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);

        var asColleague = vaultApi.get(tokenFor(TestUsers.MEMBERS_COLLEAGUE_EMAIL), VAULT);
        assertThat(asColleague.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(asColleague).get("accessLevel").asString()).isEqualTo("READ");
    }

    @Test
    @DisplayName("a member with only READ cannot see or change the member list")
    void members_asReader_returns403() {
        vaultApi.addMember(tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL), VAULT, TestUsers.MEMBERS_COLLEAGUE_EMAIL, "READ");

        var response = vaultApi.members(tokenFor(TestUsers.MEMBERS_COLLEAGUE_EMAIL), VAULT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a removed member loses access immediately")
    void removeMember_revokesAccessImmediately() {
        String manager = tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL);
        vaultApi.addMember(manager, VAULT, TestUsers.MEMBERS_COLLEAGUE_EMAIL, "WRITE");

        var removed = vaultApi.removeMember(manager, VAULT, TestUsers.MEMBERS_COLLEAGUE_ID.toString());

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var asColleague = vaultApi.get(tokenFor(TestUsers.MEMBERS_COLLEAGUE_EMAIL), VAULT);
        assertThat(asColleague.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("the last manager cannot remove themselves (409 LAST_MANAGER)")
    void removeLastManager_returns409() {
        var response = vaultApi.removeMember(
                tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL), VAULT, TestUsers.MEMBERS_MANAGER_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("LAST_MANAGER");
    }

    @Test
    @DisplayName("an auditor cannot be added to a vault")
    void addAuditor_returns400() {
        var response = vaultApi.addMember(
                tokenFor(TestUsers.MEMBERS_MANAGER_EMAIL), VAULT, TestUsers.MEMBERS_AUDITOR_EMAIL, "READ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("an administrator sees who has access without being a member (access recovery)")
    void members_asAdmin_returnsMembers() {
        var response = vaultApi.members(tokenFor(TestUsers.MEMBERS_ADMIN_EMAIL), VAULT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get(0).get("email").asString()).isEqualTo(TestUsers.MEMBERS_MANAGER_EMAIL);
        assertThat(bodyOf(response).get(0).get("accessLevel").asString()).isEqualTo("MANAGE");
    }
}