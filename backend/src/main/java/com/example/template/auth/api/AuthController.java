package com.example.template.auth.api;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.LoginResult;
import com.example.template.auth.dto.TokenPairResponse;
import com.example.template.auth.dto.TwoFactorChallengeResponse;
import com.example.template.auth.dto.TwoFactorVerifyRequest;
import com.example.template.auth.service.AuthService;
import com.example.template.shared.security.RefreshTokenCookieFactory;
import com.example.template.user.dto.RegisterUserRequest;
import com.example.template.user.dto.UserResponse;
import com.example.template.user.service.UserService;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous entry points only; refresh and logout authenticate by cookie alone (their CSRF
 * defense is the origin check in the filter chain). The refresh token travels only as an
 * HttpOnly cookie.
 */
@RestController
@RequestMapping("/api/v{version}/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(
            AuthService authService, UserService userService, RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.authService = authService;
        this.userService = userService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @PostMapping(path = "/register", version = "1")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userService.register(request);
    }

    @PostMapping(path = "/login", version = "1")
    @PreAuthorize("permitAll()")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return switch (result) {
            case TokenPairResponse tokens -> withRefreshCookie(tokens);
            case TwoFactorChallengeResponse challenge -> ResponseEntity.ok(challenge);
        };
    }

    @PostMapping(path = "/2fa/verify", version = "1")
    @PreAuthorize("permitAll()")
    public ResponseEntity<LoginResult> verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return withRefreshCookie(authService.verifyTwoFactor(request));
    }

    @PostMapping(path = "/refresh", version = "1")
    @PreAuthorize("permitAll()")
    public ResponseEntity<LoginResult> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) @Nullable
            String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping(path = "/logout", version = "1")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) @Nullable
            String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResult> withRefreshCookie(TokenPairResponse tokens) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .body(tokens);
    }
}
