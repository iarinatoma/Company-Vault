package com.example.template.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmTest {

    @Test
    @DisplayName("decrypting what was encrypted gives the original text back")
    void encryptThenDecrypt_returnsOriginal() {
        byte[] key = AesGcm.newKey();

        String encrypted = AesGcm.encrypt(key, "db-password-123");

        assertThat(encrypted).doesNotContain("db-password-123");
        assertThat(AesGcm.decrypt(key, encrypted)).isEqualTo("db-password-123");
    }

    @Test
    @DisplayName("the same text encrypts differently every time (random nonce)")
    void sameText_encryptsDifferently() {
        byte[] key = AesGcm.newKey();

        assertThat(AesGcm.encrypt(key, "same")).isNotEqualTo(AesGcm.encrypt(key, "same"));
    }

    @Test
    @DisplayName("a different key cannot decrypt")
    void wrongKey_fails() {
        String encrypted = AesGcm.encrypt(AesGcm.newKey(), "secret");

        assertThatThrownBy(() -> AesGcm.decrypt(AesGcm.newKey(), encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tampered ciphertext is detected")
    void tamperedCiphertext_fails() {
        byte[] key = AesGcm.newKey();
        byte[] raw = Base64.getDecoder().decode(AesGcm.encrypt(key, "secret"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> AesGcm.decrypt(key, tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("keys are 32 random bytes and other lengths are rejected")
    void keys_are32RandomBytes() {
        assertThat(AesGcm.newKey()).hasSize(32).isNotEqualTo(AesGcm.newKey());
        assertThatThrownBy(() -> AesGcm.encrypt(new byte[16], "x")).isInstanceOf(IllegalArgumentException.class);
    }
}