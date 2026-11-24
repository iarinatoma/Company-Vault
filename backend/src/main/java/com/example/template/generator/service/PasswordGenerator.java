package com.example.template.generator.service;

import com.example.template.generator.dto.GeneratedPasswordResponse;
import com.example.template.generator.dto.PasswordStrength;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Built-in password generator (spec 4.3). Characters come from {@link SecureRandom}; every chosen
 * character set appears at least once, the rest is drawn from all chosen sets, and the result is
 * shuffled so the guaranteed characters are not always at the front. Nothing is stored or logged.
 */
@Service
public class PasswordGenerator {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String DIGITS = "0123456789";
    static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/";

    private final SecureRandom random = new SecureRandom();

    public GeneratedPasswordResponse generate(
            int length, boolean lowercase, boolean uppercase, boolean digits, boolean symbols) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        List<String> sets = new ArrayList<>();
        if (lowercase) {
            sets.add(LOWERCASE);
        }
        if (uppercase) {
            sets.add(UPPERCASE);
        }
        if (digits) {
            sets.add(DIGITS);
        }
        if (symbols) {
            sets.add(SYMBOLS);
        }
        if (sets.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }

        String pool = String.join("", sets);
        char[] password = new char[length];
        int position = 0;
        for (String set : sets) {
            password[position++] = randomCharOf(set);
        }
        while (position < length) {
            password[position++] = randomCharOf(pool);
        }
        shuffle(password);

        double entropyBits = entropyBits(length, pool.length());
        return new GeneratedPasswordResponse(
                new String(password), (int) Math.round(entropyBits), PasswordStrength.of(entropyBits));
    }

    /** Entropy of a random password: length × log2(pool size). */
    static double entropyBits(int length, int poolSize) {
        return length * (Math.log(poolSize) / Math.log(2));
    }

    private char randomCharOf(String set) {
        return set.charAt(random.nextInt(set.length()));
    }

    /** Fisher–Yates shuffle with the same SecureRandom. */
    private void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
    }
}