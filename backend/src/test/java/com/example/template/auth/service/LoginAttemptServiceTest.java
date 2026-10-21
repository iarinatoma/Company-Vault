package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.auth.event.AccountLockedEvent;
import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.security.Role;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService")
class LoginAttemptServiceTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final AppSecurityProperties PROPERTIES = new AppSecurityProperties(
            new AppSecurityProperties.Jwt(
                    "unit-test-secret-with-at-least-32-bytes",
                    Duration.ofMinutes(15),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(60)),
            new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
            new AppSecurityProperties.RefreshToken(Duration.ofDays(30), Duration.ofDays(7)),
            new AppSecurityProperties.Lockout(MAX_ATTEMPTS, LOCKOUT_DURATION),
            new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
            new AppSecurityProperties.Cors(List.of("https://app.example.com")),
            new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(new byte[32])));

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(userRepository, PROPERTIES, passwordEncoder, eventPublisher);
    }

    private static User user() {
        var user = new User();
        user.setEmail("someone@example.com");
        user.setPasswordHash("{bcrypt}hash");
        user.setRole(Role.USER);
        return user;
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailureTests {

        @Test
        @DisplayName("does nothing and publishes no event when the user id is unknown")
        void recordFailure_unknownUserId_publishesNothing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            loginAttemptService.recordFailure(USER_ID);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("increments the counter without locking while below the threshold")
        void recordFailure_belowThreshold_incrementsCounterWithoutEvent() {
            var target = user();
            target.setFailedLoginAttempts(MAX_ATTEMPTS - 2);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

            loginAttemptService.recordFailure(USER_ID);

            assertThat(target.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
            assertThat(target.getLockedUntil()).isNull();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("locks the account, resets the counter and publishes AccountLockedEvent at the threshold")
        void recordFailure_reachesThreshold_locksAccountAndPublishesEvent() {
            var target = user();
            target.setFailedLoginAttempts(MAX_ATTEMPTS - 1);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

            loginAttemptService.recordFailure(USER_ID);

            assertThat(target.getLockedUntil()).isNotNull().isAfter(Instant.now());
            assertThat(target.getFailedLoginAttempts()).isZero();
            var event = ArgumentCaptor.forClass(AccountLockedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().userId()).isEqualTo(USER_ID);
            assertThat(event.getValue().email()).isEqualTo(target.getEmail());
            assertThat(event.getValue().lockedUntil()).isEqualTo(target.getLockedUntil());
        }
    }

    @Nested
    @DisplayName("reset")
    class ResetTests {

        @Test
        @DisplayName("clears the failure counter and the lock for a locked user")
        void reset_lockedUser_clearsCounterAndLock() {
            var target = user();
            target.setFailedLoginAttempts(2);
            target.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

            loginAttemptService.reset(USER_ID);

            assertThat(target.getFailedLoginAttempts()).isZero();
            assertThat(target.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("leaves an already-clean user untouched")
        void reset_cleanUser_leavesFieldsUntouched() {
            var target = user();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));

            loginAttemptService.reset(USER_ID);

            assertThat(target.getFailedLoginAttempts()).isZero();
            assertThat(target.getLockedUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("upgradePasswordIfNeeded")
    class UpgradePasswordIfNeededTests {

        private static final String RAW_PASSWORD = "long-password-123";

        @Test
        @DisplayName("never re-encodes when the stored hash meets current parameters")
        void upgradePasswordIfNeeded_hashCurrent_leavesHashUnchanged() {
            var target = user();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));
            when(passwordEncoder.upgradeEncoding("{bcrypt}hash")).thenReturn(false);

            loginAttemptService.upgradePasswordIfNeeded(USER_ID, RAW_PASSWORD);

            assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}hash");
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("re-encodes the raw password and replaces the stored hash when below current parameters")
        void upgradePasswordIfNeeded_hashOutdated_reencodesAndReplacesHash() {
            var target = user();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));
            when(passwordEncoder.upgradeEncoding("{bcrypt}hash")).thenReturn(true);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("{bcrypt}upgraded");

            loginAttemptService.upgradePasswordIfNeeded(USER_ID, RAW_PASSWORD);

            assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}upgraded");
            verify(passwordEncoder).encode(RAW_PASSWORD);
        }
    }
}
