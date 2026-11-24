package com.example.template.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM with a caller-supplied key — used for the per-vault data keys (DEKs).
 * Same format as {@link AesGcmEncryptor}: base64 of nonce (12 bytes) || ciphertext || tag (16 bytes),
 * with a fresh random nonce for every encryption.
 */
public final class AesGcm {

    public static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcm() {}

    /** A new random 256-bit key. */
    public static byte[] newKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /** Encrypts UTF-8 plaintext; returns base64 of nonce || ciphertext || tag. */
    public static String encrypt(byte[] key, String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(key), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Reverses {@link #encrypt}; throws {@code IllegalStateException} on a wrong key or tampered input. */
    public static String decrypt(byte[] key, String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey(key),
                    new GCMParameterSpec(TAG_LENGTH_BITS, decoded, 0, NONCE_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(decoded, NONCE_LENGTH_BYTES, decoded.length - NONCE_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private static SecretKeySpec aesKey(byte[] key) {
        if (key.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256 needs a key of exactly 32 bytes");
        }
        return new SecretKeySpec(key, "AES");
    }
}