package com.example.template.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Password policy, following the NIST SP 800-63B idea: length matters most, so the minimum is
 * high (12) and there are no complex composition rules — only one letter and one digit, plus a
 * blocklist of well-known passwords and a guard against trivial repetition ("aaaaaaaaaaa1").
 * The frontend mirrors the length and letter/digit rules for instant feedback; this class is
 * the authority.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** At least this many different characters — rejects "aaaaaaaaaaa1" and similar. */
    static final int MIN_DISTINCT_CHARACTERS = 5;

    /** Compared case-insensitively. Only entries that would otherwise pass the other rules matter. */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234",
            "password12345",
            "password123456",
            "passw0rd1234",
            "qwerty123456",
            "qwertz123456",
            "1q2w3e4r5t6y",
            "1qaz2wsx3edc",
            "abc123456789",
            "abcd12345678",
            "admin1234567",
            "administrator1",
            "letmein12345",
            "welcome12345",
            "iloveyou1234",
            "changeme1234",
            "company12345",
            "companyvault1",
            "passwort1234",
            "parola123456");

    @Override
    public boolean isValid(@Nullable String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return false;
        }
        if (password.codePoints().distinct().count() < MIN_DISTINCT_CHARACTERS) {
            return false;
        }
        return !COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT));
    }
}