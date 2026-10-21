package com.example.template.user.integration;

import com.github.database.rider.core.api.dataset.DataSet;
import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.integration.support.UserApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} and
 * {@code @DBUnit} via {@code Class#getAnnotation}, which sees inherited class-level
 * annotations but never those of an enclosing class — a nested group silently runs unseeded.
 */
@DataSet("datasets/user-users.yml")
@DisplayName("User API")
class UserControllerIT extends AbstractIntegrationTest {

    private AuthApiClient authApi;
    private UserApiClient userApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
        userApi = new UserApiClient(restTemplate);
    }

    // --- GET /users/me ---

    @Test
    @DisplayName("returns the caller's own profile")
    void getOwnProfile_authenticated_returnsProfile() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.getOwnProfile(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("email").asString()).isEqualTo(TestUsers.USER_A_EMAIL);
        assertThat(bodyOf(response).get("id").asString()).isEqualTo(TestUsers.USER_A_ID.toString());
    }

    @Test
    @DisplayName("answers 401 without a token")
    void getOwnProfile_anonymous_returns401() {
        var response = userApi.getOwnProfile(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- GET /users/{id} ---

    @Test
    @DisplayName("lets a user read their own record")
    void getById_ownId_returnsUser() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.getById(token, TestUsers.USER_A_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("email").asString()).isEqualTo(TestUsers.USER_A_EMAIL);
    }

    @Test
    @DisplayName("denies a user another user's record — scope isolation, not just the role annotation")
    void getById_otherUsersId_returns403Forbidden() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.getById(token, TestUsers.USER_B_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("lets an admin read any record")
    void getById_asAdmin_returnsAnyUser() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.getById(token, TestUsers.USER_B_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- GET /users ---

    @Test
    @DisplayName("returns the page envelope for an admin")
    void list_asAdmin_returnsPageEnvelope() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.list(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("content").isArray()).isTrue();
        assertThat(bodyOf(response).get("page").has("totalElements")).isTrue();
    }

    @Test
    @DisplayName("denies a plain user the listing with 403")
    void list_asUser_returns403() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.list(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("fails deterministically for an unsupported API version — never a silent 404")
    void list_unsupportedVersion_returns400UnsupportedApiVersion() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.listAtVersion(token, "v9");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("UNSUPPORTED_API_VERSION");
    }

    // --- POST /users ---

    @Test
    @DisplayName("creates a user for an admin")
    void create_asAdmin_returns201() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);
        // Unique-minted: the shared database survives across classes and runs.
        String email = "created-" + UUID.randomUUID() + "@test.local";

        var response = userApi.create(token, email, "brand-new-password-1", "USER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bodyOf(response).get("email").asString()).isEqualTo(email);
    }

    @Test
    @DisplayName("answers 409 EMAIL_ALREADY_IN_USE for a duplicate email")
    void create_duplicateEmail_returns409() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.create(token, TestUsers.USER_A_EMAIL, "brand-new-password-1", "USER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("EMAIL_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("denies user creation to a plain user with 403")
    void create_asUser_returns403() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.create(token, "nope-" + UUID.randomUUID() + "@test.local", "some-password-123", "USER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("answers 400 VALIDATION_FAILED with field errors for an invalid body")
    void create_invalidBody_returns400ValidationFailed() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.create(token, "not-an-email", "short", "USER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(bodyOf(response).has("errors")).isTrue();
    }

    // --- PUT /users/{id} ---

    @Test
    @DisplayName("updates e-mail and role for an admin")
    void update_asAdmin_returns200() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);
        String email = "updated-" + UUID.randomUUID() + "@test.local";

        var response = userApi.update(token, TestUsers.USER_B_ID.toString(), email, "MANAGER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("email").asString()).isEqualTo(email);
        assertThat(bodyOf(response).get("role").asString()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("answers 409 EMAIL_ALREADY_IN_USE when the new e-mail belongs to someone else")
    void update_emailOfAnotherUser_returns409() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.update(token, TestUsers.USER_A_ID.toString(), TestUsers.ADMIN_EMAIL, "USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("EMAIL_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("answers 409 CANNOT_MODIFY_OWN_ACCOUNT when an admin changes their own role")
    void update_ownRole_returns409() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.update(token, TestUsers.ADMIN_ID.toString(), TestUsers.ADMIN_EMAIL, "USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("CANNOT_MODIFY_OWN_ACCOUNT");
    }

    @Test
    @DisplayName("answers 404 USER_NOT_FOUND for an unknown id")
    void update_unknownId_returns404() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.update(token, UUID.randomUUID().toString(), "x@test.local", "USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("denies updates to a plain user with 403")
    void update_asUser_returns403() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.update(token, TestUsers.USER_B_ID.toString(), TestUsers.USER_B_EMAIL, "USER", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- DELETE /users/{id} ---

    @Test
    @DisplayName("deletes a user (and the auth data of that user) for an admin")
    void delete_asAdmin_returns204() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);
        String email = "doomed-" + UUID.randomUUID() + "@test.local";
        String id = bodyOf(userApi.create(token, email, "brand-new-password-1", "USER")).get("id").asString();
        // Signing in creates a refresh token row that must not block the delete (FK).
        authApi.accessTokenFor(email, "brand-new-password-1");

        var response = userApi.delete(token, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userApi.getById(token, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("answers 409 CANNOT_MODIFY_OWN_ACCOUNT when an admin deletes themselves")
    void delete_self_returns409() {
        String token = authApi.accessTokenFor(TestUsers.ADMIN_EMAIL, TestUsers.PASSWORD);

        var response = userApi.delete(token, TestUsers.ADMIN_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("CANNOT_MODIFY_OWN_ACCOUNT");
    }

    @Test
    @DisplayName("denies deletion to a plain user with 403")
    void delete_asUser_returns403() {
        String token = authApi.accessTokenFor(TestUsers.USER_A_EMAIL, TestUsers.PASSWORD);

        var response = userApi.delete(token, TestUsers.USER_B_ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
