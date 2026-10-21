package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.auth.model.RecoveryCode;
import com.example.template.auth.repository.RecoveryCodeRepository;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.shared.security.Role;
import com.example.template.user.model.User;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("TotpService")
class TotpServiceTest {

    @Mock
    private AeadEncryptor aeadEncryptor;

    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TotpService totpService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOTP_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String ENCRYPTED_SECRET = "encrypted-totp-secret";
    private static final int TIME_PERIOD_SECONDS = 30;

    private static User user() {
        var user = new User();
        user.setEmail("someone@example.com");
        user.setPasswordHash("{bcrypt}hash");
        user.setRole(Role.USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private static RecoveryCode recoveryCode(String codeHash) {
        var recoveryCode = new RecoveryCode();
        recoveryCode.setUserId(USER_ID);
        recoveryCode.setCodeHash(codeHash);
        return recoveryCode;
    }

    /** A code that is genuinely valid right now — the verifier's discrepancy window makes the timing safe. */
    private static String currentTotpCode() throws CodeGenerationException {
        long counter = Math.floorDiv(Instant.now().getEpochSecond(), TIME_PERIOD_SECONDS);
        return new DefaultCodeGenerator().generate(TOTP_SECRET, counter);
    }

    /** Shifts every digit so the result differs from the valid code in all six positions. */
    private static String wrongTotpCode(String validCode) {
        var wrong = new StringBuilder();
        for (char digit : validCode.toCharArray()) {
            wrong.append((char) ('0' + (digit - '0' + 5) % 10));
        }
        return wrong.toString();
    }

    @Nested
    @DisplayName("verify")
    class VerifyTests {

        @Test
        @DisplayName("returns true for a currently valid TOTP code without ever querying recovery codes")
        void verify_validTotpCode_returnsTrueWithoutQueryingRecoveryCodes() throws CodeGenerationException {
            var user = user();
            user.setTotpSecret(ENCRYPTED_SECRET);
            when(aeadEncryptor.decrypt(ENCRYPTED_SECRET)).thenReturn(TOTP_SECRET);

            var result = totpService.verify(user, currentTotpCode());

            assertThat(result).isTrue();
            verify(recoveryCodeRepository, never()).findAllByUserIdAndUsedAtIsNull(any());
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("returns false for a wrong TOTP code when no recovery code matches either")
        void verify_wrongTotpCodeAndNoMatchingRecoveryCode_returnsFalse() throws CodeGenerationException {
            var user = user();
            user.setTotpSecret(ENCRYPTED_SECRET);
            var storedCode = recoveryCode("{bcrypt}recovery-hash");
            String code = wrongTotpCode(currentTotpCode());
            when(aeadEncryptor.decrypt(ENCRYPTED_SECRET)).thenReturn(TOTP_SECRET);
            when(recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(storedCode));
            when(passwordEncoder.matches(code, "{bcrypt}recovery-hash")).thenReturn(false);

            var result = totpService.verify(user, code);

            assertThat(result).isFalse();
            assertThat(storedCode.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("returns true and consumes the recovery code when the user has no TOTP secret")
        void verify_noTotpSecretButRecoveryCodeMatches_returnsTrueAndConsumesCode() {
            var user = user();
            var storedCode = recoveryCode("{bcrypt}recovery-hash");
            when(recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(storedCode));
            when(passwordEncoder.matches("recovery-code", "{bcrypt}recovery-hash")).thenReturn(true);

            var result = totpService.verify(user, "recovery-code");

            assertThat(result).isTrue();
            assertThat(storedCode.getUsedAt()).isNotNull();
            verify(aeadEncryptor, never()).decrypt(any());
        }

        @Test
        @DisplayName("consumes only the matching recovery code when several are outstanding")
        void verify_recoveryCodeMatchesAmongSeveral_consumesOnlyMatchingCode() {
            var user = user();
            var otherCode = recoveryCode("{bcrypt}other-hash");
            var matchingCode = recoveryCode("{bcrypt}matching-hash");
            when(recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(USER_ID))
                    .thenReturn(List.of(otherCode, matchingCode));
            when(passwordEncoder.matches("recovery-code", "{bcrypt}other-hash")).thenReturn(false);
            when(passwordEncoder.matches("recovery-code", "{bcrypt}matching-hash")).thenReturn(true);

            var result = totpService.verify(user, "recovery-code");

            assertThat(result).isTrue();
            assertThat(matchingCode.getUsedAt()).isNotNull();
            assertThat(otherCode.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("returns false and mutates no recovery code when nothing matches")
        void verify_nothingMatches_returnsFalseWithoutConsumingAnyCode() {
            var user = user();
            var storedCode = recoveryCode("{bcrypt}recovery-hash");
            when(recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(storedCode));
            when(passwordEncoder.matches("not-a-code", "{bcrypt}recovery-hash")).thenReturn(false);

            var result = totpService.verify(user, "not-a-code");

            assertThat(result).isFalse();
            assertThat(storedCode.getUsedAt()).isNull();
        }
    }
}
