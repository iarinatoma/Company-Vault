package com.example.template.auth.service;

import com.example.template.auth.dto.LoginRequest;
import com.example.template.auth.dto.LoginResult;
import com.example.template.auth.dto.TokenPairResponse;
import com.example.template.auth.dto.TwoFactorChallengeResponse;
import com.example.template.auth.dto.TwoFactorVerifyRequest;
import com.example.template.auth.model.ConsumedTwoFactorToken;
import com.example.template.auth.repository.ConsumedTwoFactorTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.JwtService;
import com.example.template.shared.security.TokenType;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Auth log safelist: the identifier and the stable error code — never tokens,
    // credentials or cookie values.
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * An unknown email still runs the encoder against this fixed hash so user enumeration by
     * timing is dead. The value is a syntactically valid bcrypt-12 hash; whether anything
     * matches it is irrelevant — the result is discarded.
     */
    private static final String DUMMY_HASH = "{bcrypt}$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final SessionRevoker sessionRevoker;
    private final TotpService totpService;
    private final ConsumedTwoFactorTokenRepository consumedTwoFactorTokenRepository;
    private final AppSecurityProperties properties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService,
            SessionRevoker sessionRevoker,
            TotpService totpService,
            ConsumedTwoFactorTokenRepository consumedTwoFactorTokenRepository,
            AppSecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.sessionRevoker = sessionRevoker;
        this.totpService = totpService;
        this.consumedTwoFactorTokenRepository = consumedTwoFactorTokenRepository;
        this.properties = properties;
    }

    /**
     * Answers 401 INVALID_CREDENTIALS identically for unknown email, wrong password and
     * locked account — and takes the same time for all three.
     */
    @Transactional
    public LoginResult login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            log.warn("Login failed for {}: unknown identifier (INVALID_CREDENTIALS)", request.email());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        boolean locked = isLocked(user);
        if (!passwordMatches) {
            if (!locked) {
                loginAttemptService.recordFailure(user.requireId());
            }
            log.warn("Login failed for {}: wrong password (INVALID_CREDENTIALS)", request.email());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (locked) {
            log.warn("Login failed for {}: account locked (INVALID_CREDENTIALS)", request.email());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptService.upgradePasswordIfNeeded(user.requireId(), request.password());
        if (user.isTwoFactorEnabled()) {
            // The counter only resets once the second factor verifies.
            log.info("Two-factor challenge issued for {}", request.email());
            return new TwoFactorChallengeResponse(
                    jwtService.mintTwoFactorToken(user.requireId()),
                    properties.jwt().twoFactorTtl().toSeconds());
        }
        loginAttemptService.reset(user.requireId());
        log.info("Login succeeded for {}", request.email());
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public TokenPairResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        var claims = jwtService.parse(request.challengeToken(), TokenType.TWO_FACTOR);
        if (consumedTwoFactorTokenRepository.existsByJti(claims.jti())) {
            // Replay of an already-consumed challenge token.
            log.warn("Replayed two-factor challenge token for user {} (INVALID_TOKEN)", claims.userId());
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        var user =
                userRepository.findById(claims.userId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (isLocked(user)) {
            log.warn("Two-factor verification rejected for {}: account locked (INVALID_CREDENTIALS)", user.getEmail());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!totpService.verify(user, request.code())) {
            loginAttemptService.recordFailure(user.requireId());
            log.warn("Two-factor verification failed for {} (INVALID_CREDENTIALS)", user.getEmail());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        var consumed = new ConsumedTwoFactorToken();
        consumed.setJti(claims.jti());
        consumed.setExpiresAt(claims.expiresAt());
        consumedTwoFactorTokenRepository.save(consumed);
        loginAttemptService.reset(user.requireId());
        log.info("Two-factor verification succeeded for {}", user.getEmail());
        return issueTokenPair(user, UUID.randomUUID());
    }

    @Transactional
    public TokenPairResponse refresh(@Nullable String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        var stored =
                refreshTokenService.find(rawToken).orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (stored.getRevokedAt() != null) {
            // A rotated token came back: someone replayed it. Kill the whole family.
            sessionRevoker.revokeFamily(stored.getFamilyId());
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        var user = userRepository
                .findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (isLocked(user)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        stored.setRevokedAt(Instant.now());
        return issueTokenPair(user, stored.getFamilyId());
    }

    /**
     * Revokes the stored refresh token server-side; the access token stays valid until it
     * expires — that is the design, and the short access TTL is what bounds it.
     */
    @Transactional
    public void logout(@Nullable String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenService.find(rawToken).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            log.info("Refresh token revoked on logout for user {}", token.getUserId());
        });
    }

    private TokenPairResponse issueTokenPair(User user, UUID familyId) {
        var issued = refreshTokenService.issue(user.requireId(), familyId);
        String accessToken = jwtService.mintAccessToken(user.requireId(), user.getEmail(), user.getRole());
        return new TokenPairResponse(
                accessToken, "Bearer", properties.jwt().accessTtl().toSeconds(), issued.rawToken());
    }

    private static boolean isLocked(User user) {
        var lockedUntil = user.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
