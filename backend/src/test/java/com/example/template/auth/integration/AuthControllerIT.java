package com.example.template.auth.integration;

import com.github.database.rider.core.api.dataset.DataSet;
import com.example.template.integration.AbstractIntegrationTest;
import com.example.template.integration.support.AuthApiClient;
import com.example.template.integration.support.TestUsers;
import com.example.template.integration.support.UserApiClient;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.shared.security.JwtService;
import com.example.template.user.repository.UserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No {@code @Nested} groups here on purpose: database-rider resolves {@code @DataSet} and
 * {@code @DBUnit} via {@code Class#getAnnotation}, which sees inherited class-level
 * annotations but never those of an enclosing class — a nested group silently runs unseeded.
 */
@DataSet("datasets/auth-users.yml")
@DisplayName("Auth API")
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AeadEncryptor aeadEncryptor;

    private AuthApiClient authApi;
    private UserApiClient userApi;
    private String totpSecret;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiClient(restTemplate);
        userApi = new UserApiClient(restTemplate);
        // Enroll the 2FA fixture user programmatically: the secret is AEAD-encrypted at rest,
        // so a static fixture value cannot express it.
        totpSecret = new DefaultSecretGenerator().generate();
        var twoFactorUser =
                userRepository.findByEmail(TestUsers.AUTH_2FA_USER_EMAIL).orElseThrow();
        twoFactorUser.setTotpSecret(aeadEncryptor.encrypt(totpSecret));
        userRepository.save(twoFactorUser);
    }

    private String currentTotpCode() throws CodeGenerationException {
        long counter = new SystemTimeProvider().getTime() / 30;
        return new DefaultCodeGenerator().generate(totpSecret, counter);
    }

    // --- login ---

    @Test
    @DisplayName("login returns an access token in the body and the refresh token only as an HttpOnly cookie")
    void login_validCredentials_returnsTokensWithRefreshCookie() {
        var response = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = bodyOf(response);
        assertThat(body.hasNonNull("accessToken")).isTrue();
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        // The refresh token must never appear in a response body.
        assertThat(body.has("refreshToken")).isFalse();

        String setCookie = AuthApiClient.refreshSetCookieHeaderOf(response);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("login answers 401 INVALID_CREDENTIALS for a wrong password")
    void login_wrongPassword_returns401InvalidCredentials() {
        var response = authApi.login(TestUsers.AUTH_USER_EMAIL, "wrong-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("login answers the identical 401 INVALID_CREDENTIALS for an unknown email")
    void login_unknownEmail_returns401InvalidCredentials() {
        var response = authApi.login("nobody@test.local", TestUsers.PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("login answers the identical 401 INVALID_CREDENTIALS for a locked account with the right password")
    void login_lockedAccount_returns401InvalidCredentials() {
        var response = authApi.login(TestUsers.AUTH_LOCKED_USER_EMAIL, TestUsers.PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    // --- token type enforcement ---

    @Test
    @DisplayName("a 2FA challenge token presented as an access token is rejected")
    void accessProtectedEndpoint_withTwoFactorToken_returns401() {
        String twoFactorToken = jwtService.mintTwoFactorToken(TestUsers.AUTH_USER_ID);

        var response = userApi.getOwnProfile(twoFactorToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an access token presented as a 2FA challenge token is rejected")
    void verifyTwoFactor_withAccessToken_returns401() {
        String accessToken = authApi.accessTokenFor(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);

        var response = authApi.verifyTwoFactor(accessToken, "123456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("INVALID_TOKEN");
    }

    // --- 2FA verification ---

    @Test
    @DisplayName("login answers a challenge, a valid TOTP code completes it, and the challenge cannot be replayed")
    void twoFactorFlow_validCode_returnsTokensAndConsumesChallenge() throws CodeGenerationException {
        var loginResponse = authApi.login(TestUsers.AUTH_2FA_USER_EMAIL, TestUsers.PASSWORD);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var loginBody = bodyOf(loginResponse);
        assertThat(loginBody.hasNonNull("challengeToken")).isTrue();
        assertThat(loginBody.has("accessToken")).isFalse();
        assertThat(AuthApiClient.refreshSetCookieHeaderOf(loginResponse)).isNull();

        String challengeToken = loginBody.get("challengeToken").asString();
        var verifyResponse = authApi.verifyTwoFactor(challengeToken, currentTotpCode());
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(verifyResponse).hasNonNull("accessToken")).isTrue();
        assertThat(AuthApiClient.refreshCookieOf(verifyResponse)).isNotNull();

        // The jti was consumed — replaying the same challenge must fail.
        var replayResponse = authApi.verifyTwoFactor(challengeToken, currentTotpCode());
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(replayResponse).get("code").asString()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("2FA verification answers 401 INVALID_CREDENTIALS for a wrong code")
    void verifyTwoFactor_wrongCode_returns401InvalidCredentials() {
        var loginResponse = authApi.login(TestUsers.AUTH_2FA_USER_EMAIL, TestUsers.PASSWORD);
        String challengeToken = bodyOf(loginResponse).get("challengeToken").asString();

        var verifyResponse = authApi.verifyTwoFactor(challengeToken, "000000");

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(verifyResponse).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    // --- refresh ---

    @Test
    @DisplayName("refresh rotates the refresh token and issues a fresh pair")
    void refresh_validCookie_rotatesToken() {
        var loginResponse = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);
        String cookie = AuthApiClient.refreshCookieOf(loginResponse);

        var refreshResponse = authApi.refresh(cookie, null);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(refreshResponse).hasNonNull("accessToken")).isTrue();
        String rotatedCookie = AuthApiClient.refreshCookieOf(refreshResponse);
        assertThat(rotatedCookie).isNotNull().isNotEqualTo(cookie);
    }

    @Test
    @DisplayName("replaying a rotated refresh token revokes the whole session family")
    void refresh_reusedRotatedToken_revokesFamily() {
        var loginResponse = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);
        String originalCookie = AuthApiClient.refreshCookieOf(loginResponse);
        String rotatedCookie = AuthApiClient.refreshCookieOf(authApi.refresh(originalCookie, null));

        // Replay the predecessor: rejected, and the whole family dies with it.
        var replayResponse = authApi.refresh(originalCookie, null);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(replayResponse).get("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");

        var familyResponse = authApi.refresh(rotatedCookie, null);
        assertThat(familyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh answers 401 and clears the cookie when no refresh cookie is present")
    void refresh_missingCookie_returns401AndClearsCookie() {
        var response = authApi.refresh(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(response).get("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");
        String clearingCookie = AuthApiClient.refreshSetCookieHeaderOf(response);
        assertThat(clearingCookie).isNotNull();
        assertThat(clearingCookie).contains("Max-Age=0");
    }

    @Test
    @DisplayName("refresh rejects a disallowed Origin with 403 before touching the cookie")
    void refresh_disallowedOrigin_returns403OriginNotAllowed() {
        var loginResponse = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);
        String cookie = AuthApiClient.refreshCookieOf(loginResponse);

        // Spring Security's CorsFilter answers first for a present-but-disallowed Origin
        // (bodyless 403); AuthOriginValidationFilter still backstops the Referer-only case.
        var response = authApi.refreshRaw(cookie, "https://evil.example");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("refresh accepts an allowlisted Origin")
    void refresh_allowedOrigin_returns200() {
        var loginResponse = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);
        String cookie = AuthApiClient.refreshCookieOf(loginResponse);

        var response = authApi.refresh(cookie, "http://localhost:4200");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- logout ---

    @Test
    @DisplayName("logout revokes the refresh token server-side and clears the cookie")
    void logout_withCookie_revokesTokenAndClearsCookie() {
        var loginResponse = authApi.login(TestUsers.AUTH_USER_EMAIL, TestUsers.PASSWORD);
        String cookie = AuthApiClient.refreshCookieOf(loginResponse);

        var logoutResponse = authApi.logout(cookie);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String clearingCookie = AuthApiClient.refreshSetCookieHeaderOf(logoutResponse);
        assertThat(clearingCookie).isNotNull();
        assertThat(clearingCookie).contains("Max-Age=0");

        var refreshResponse = authApi.refresh(cookie, null);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
