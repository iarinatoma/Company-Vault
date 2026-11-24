package com.example.template.generator.dto;

/**
 * How hard a password is to guess, judged by its entropy in bits. The frontend strength meter
 * uses the same thresholds.
 */
public enum PasswordStrength {
    WEAK,
    FAIR,
    STRONG,
    VERY_STRONG;

    public static PasswordStrength of(double entropyBits) {
        if (entropyBits < 40) {
            return WEAK;
        }
        if (entropyBits < 60) {
            return FAIR;
        }
        if (entropyBits < 80) {
            return STRONG;
        }
        return VERY_STRONG;
    }
}