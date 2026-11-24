package com.example.template.shared.crypto;

import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.config.KeyRotationProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM with a random 96-bit nonce per encryption, key from the environment
 * ({@code app.security.encryption.key}). During a master key rotation the previous key
 * ({@code app.security.encryption.previous-key}) is accepted for decryption only, so data still
 * under the old key stays readable until it has been re-encrypted.
 */
@Component
public class AesGcmEncryptor implements AeadEncryptor {

    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final @Nullable SecretKeySpec previousKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptor(AppSecurityProperties properties, KeyRotationProperties rotation) {
        this.key = new SecretKeySpec(properties.encryption().keyBytes(), "AES");
        byte @Nullable [] previous = rotation.previousKeyBytes();
        this.previousKey = previous == null ? null : new SecretKeySpec(previous, "AES");
    }

    /** Always the current key: everything new is encrypted with it. */
    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Tries the current key first; during a rotation falls back to the previous one. */
    @Override
    public String decrypt(String encrypted) {
        try {
            return decryptWith(key, encrypted);
        } catch (IllegalStateException withCurrentKey) {
            if (previousKey == null) {
                throw withCurrentKey;
            }
            try {
                return decryptWith(previousKey, encrypted);
            } catch (IllegalStateException withPreviousKey) {
                withCurrentKey.addSuppressed(withPreviousKey);
                throw withCurrentKey;
            }
        }
    }

    private static String decryptWith(SecretKeySpec decryptionKey, String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    decryptionKey,
                    new GCMParameterSpec(TAG_LENGTH_BITS, decoded, 0, NONCE_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(decoded, NONCE_LENGTH_BYTES, decoded.length - NONCE_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Not base64, or shorter than a nonce: malformed input fails like tampered input.
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }
}