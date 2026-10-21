package com.example.template.shared.crypto;

/**
 * Authenticated encryption for secrets at rest (e.g. TOTP secrets — never stored plaintext).
 */
public interface AeadEncryptor {

    /** Encrypts UTF-8 plaintext; returns base64 of nonce || ciphertext || tag. */
    String encrypt(String plaintext);

    /** Reverses {@link #encrypt}; throws {@code IllegalStateException} on tampered input. */
    String decrypt(String encrypted);
}
