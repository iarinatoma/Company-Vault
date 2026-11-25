package com.example.template.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.template.generator.dto.PasswordStrength;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasswordGenerator")
class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    private static boolean onlyFrom(String password, String allowed) {
        return password.chars().allMatch(c -> allowed.indexOf(c) >= 0);
    }

    private static boolean containsAnyOf(String password, String set) {
        return password.chars().anyMatch(c -> set.indexOf(c) >= 0);
    }

    private static ErrorCode codeOf(Throwable exception) {
        return ((ApiException) exception).errorCode();
    }

    @Test
    @DisplayName("the password has exactly the requested length")
    void generate_hasRequestedLength() {
        for (int length : new int[] {8, 20, 128}) {
            assertThat(generator.generate(length, true, true, true, true).password()).hasSize(length);
        }
    }

    @Test
    @DisplayName("only characters from the chosen sets are used")
    void generate_usesOnlyChosenSets() {
        String digitsOnly = generator.generate(30, false, false, true, false).password();
        String lowerAndSymbols = generator.generate(30, true, false, false, true).password();

        assertThat(onlyFrom(digitsOnly, PasswordGenerator.DIGITS)).isTrue();
        assertThat(onlyFrom(lowerAndSymbols, PasswordGenerator.LOWERCASE + PasswordGenerator.SYMBOLS))
                .isTrue();
    }

    @Test
    @DisplayName("every chosen set appears at least once, even in the shortest password")
    void generate_containsEveryChosenSet() {
        for (int i = 0; i < 50; i++) {
            String password = generator.generate(8, true, true, true, true).password();

            assertThat(containsAnyOf(password, PasswordGenerator.LOWERCASE)).isTrue();
            assertThat(containsAnyOf(password, PasswordGenerator.UPPERCASE)).isTrue();
            assertThat(containsAnyOf(password, PasswordGenerator.DIGITS)).isTrue();
            assertThat(containsAnyOf(password, PasswordGenerator.SYMBOLS)).isTrue();
        }
    }

    @Test
    @DisplayName("every call gives a different password")
    void generate_isRandom() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            passwords.add(generator.generate(16, true, true, true, true).password());
        }

        assertThat(passwords).hasSize(100);
    }

    @Test
    @DisplayName("a length outside 8–128 is rejected")
    void generate_lengthOutOfRange_isRejected() {
        assertThatThrownBy(() -> generator.generate(7, true, true, true, true))
                .extracting(PasswordGeneratorTest::codeOf)
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> generator.generate(129, true, true, true, true))
                .extracting(PasswordGeneratorTest::codeOf)
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("at least one character set must be chosen")
    void generate_noCharacterSet_isRejected() {
        assertThatThrownBy(() -> generator.generate(20, false, false, false, false))
                .extracting(PasswordGeneratorTest::codeOf)
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("strength follows the entropy: short digits are weak, long mixed passwords very strong")
    void generate_reportsStrength() {
        assertThat(generator.generate(8, false, false, true, false).strength()).isEqualTo(PasswordStrength.WEAK);
        assertThat(generator.generate(12, true, false, false, false).strength()).isEqualTo(PasswordStrength.FAIR);
        assertThat(generator.generate(20, true, true, true, true).strength())
                .isEqualTo(PasswordStrength.VERY_STRONG);
    }

    @Test
    @DisplayName("entropy is length × log2(pool size)")
    void entropyBits_isLengthTimesLog2OfPool() {
        assertThat(PasswordGenerator.entropyBits(12, 26)).isBetween(56.0, 57.0);
        assertThat(generator.generate(12, true, false, false, false).entropyBits()).isEqualTo(56);
    }

    @Test
    @DisplayName("strength thresholds: 40, 60 and 80 bits")
    void strength_thresholds() {
        assertThat(PasswordStrength.of(39.9)).isEqualTo(PasswordStrength.WEAK);
        assertThat(PasswordStrength.of(40)).isEqualTo(PasswordStrength.FAIR);
        assertThat(PasswordStrength.of(60)).isEqualTo(PasswordStrength.STRONG);
        assertThat(PasswordStrength.of(80)).isEqualTo(PasswordStrength.VERY_STRONG);
    }
}