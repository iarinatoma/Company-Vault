package com.example.template.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The validator has no collaborators and ignores the context, so it is called directly —
 * no Spring, no Hibernate Validator bootstrap.
 */
@DisplayName("StrongPasswordValidator")
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean isValid(String password) {
        return validator.isValid(password, null);
    }

    @Test
    @DisplayName("accepts passwords that meet every rule")
    void validPasswords_areAccepted() {
        List<String> valid = List.of(
                "brand-new-password-1",
                "correct horse battery 9",
                "Vault2026!secure",
                "my-dog-is-called-Rex-7");

        for (String password : valid) {
            assertThat(isValid(password)).as(password).isTrue();
        }
    }

    @Test
    @DisplayName("treats null as valid, so @NotBlank decides whether the field is required")
    void nullPassword_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("rejects passwords shorter than 12 characters")
    void tooShort_isRejected() {
        assertThat(isValid("short-pw-1")).isFalse();
        assertThat(isValid("abcdefghij1")).as("11 characters").isFalse();
        assertThat(isValid("abcdefghijk1")).as("exactly 12 characters").isTrue();
    }

    @Test
    @DisplayName("rejects passwords longer than 128 characters")
    void tooLong_isRejected() {
        String exactly128 = "a1b2c3d4".repeat(16);
        assertThat(isValid(exactly128)).isTrue();
        assertThat(isValid(exactly128 + "x")).isFalse();
    }

    @Test
    @DisplayName("rejects passwords without a letter or without a digit")
    void missingLetterOrDigit_isRejected() {
        assertThat(isValid("only-letters-here")).as("no digit").isFalse();
        assertThat(isValid("1234567890123")).as("no letter").isFalse();
        assertThat(isValid("12-34-56-78-90")).as("no letter, with symbols").isFalse();
    }

    @Test
    @DisplayName("rejects trivial repetition such as 'aaaaaaaaaaa1'")
    void tooFewDistinctCharacters_isRejected() {
        assertThat(isValid("aaaaaaaaaaa1")).isFalse();
        assertThat(isValid("abababababab12")).isFalse();
    }

    @Test
    @DisplayName("rejects well-known passwords, regardless of upper/lower case")
    void commonPasswords_areRejected() {
        assertThat(isValid("password1234")).isFalse();
        assertThat(isValid("Password1234")).isFalse();
        assertThat(isValid("QWERTY123456")).isFalse();
        assertThat(isValid("1q2w3e4r5t6y")).isFalse();
    }
}