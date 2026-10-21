package com.example.template.auth.service;

import com.example.template.auth.event.AccountLockedEvent;
import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reference implementation for the REQUIRES_NEW trap: the login flow must persist the
 * failed-attempt counter and <em>then</em> reject the request, so these writes live in a
 * separate bean with their own transaction — a rollback of the caller must not undo them.
 * Self-invocation would silently defeat the propagation; always call through the proxy.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final AppSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public LoginAttemptService(
            UserRepository userRepository,
            AppSecurityProperties properties,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Failed passwords and failed 2FA verifications count into this same counter.
     * Committed even though the surrounding login transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= properties.lockout().maxAttempts()) {
                var lockedUntil = Instant.now().plus(properties.lockout().duration());
                user.setLockedUntil(lockedUntil);
                user.setFailedLoginAttempts(0);
                log.warn(
                        "Account locked until {} for {} after {} failed attempts",
                        lockedUntil,
                        user.getEmail(),
                        attempts);
                eventPublisher.publishEvent(new AccountLockedEvent(userId, user.getEmail(), lockedUntil));
            } else {
                user.setFailedLoginAttempts(attempts);
                log.warn(
                        "Failed authentication attempt {}/{} for {}",
                        attempts,
                        properties.lockout().maxAttempts(),
                        user.getEmail());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reset(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }
        });
    }

    /**
     * Rotates the stored hash forward when it is below current parameters — the
     * DelegatingPasswordEncoder makes this a re-encode, never a migration.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upgradePasswordIfNeeded(UUID userId, String rawPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                log.info("Password hash upgraded to current parameters for user {}", userId);
            }
        });
    }
}
