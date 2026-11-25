package com.example.template.generator.dto;

/**
 * A freshly generated password. It exists only in this response: it is never stored or logged.
 * {@code entropyBits} is length × log2(size of the chosen character pool), rounded.
 */
public record GeneratedPasswordResponse(String password, int entropyBits, PasswordStrength strength) {}