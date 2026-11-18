package com.example.template.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.template.shared.config.AppSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The master-key encryptor: it wraps the per-vault data keys and the TOTP secrets. Covers
 * crypto round trips, nonce freshness, tamper detection and the error contract of
 * {@link AeadEncryptor} (bad input is always an IllegalStateException).
 */
@DisplayName("AesGcmEncryptor (master key)")
class AesGcmEncryptorTest {

    private static final byte[] KEY = AesGcm.newKey();

    private final AesGcmEncryptor encryptor = encryptorWith(KEY);

    private static AesGcmEncryptor encryptorWith(byte[] key) {
        return new AesGcmEncryptor(new AppSecurityProperties(
                new AppSecurityProperties.Jwt(
                        "unit-test-secret-with-at-least-32-bytes",
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(60)),
                new AppSecurityProperties.RefreshCookie("/api/v1/auth", "Lax"),
                new AppSecurityProperties.RefreshToken(Duration.ofDays(30), Duration.ofDays(7)),
                new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppSecurityProperties.RateLimit(10, Duration.ofMinutes(1)),
                new AppSecurityProperties.Cors(List.of("https://app.example.com")),
                new AppSecurityProperties.Encryption(Base64.getEncoder().encodeToString(key))));
    }

    @Test
    @DisplayName("decrypting what was encrypted gives the original text back")
    void encryptThenDecrypt_returnsOriginal() {
        String encrypted = encryptor.encrypt("db-password-123");

        assertThat(encryptor.decrypt(encrypted)).isEqualTo("db-password-123");
    }

    @Test
    @DisplayName("empty text and non-ASCII text survive the round trip")
    void emptyAndUnicodeText_roundTrip() {
        String unicode = "Parol\u0103 \u00C4\u00D6\u00DC\u00DF \u79D8\u5BC6 \uD83D\uDD10";

        assertThat(encryptor.decrypt(encryptor.encrypt(""))).isEmpty();
        assertThat(encryptor.decrypt(encryptor.encrypt(unicode))).isEqualTo(unicode);
    }

    @Test
    @DisplayName("the same text encrypts differently every time (random nonce)")
    void sameText_encryptsDifferently() {
        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    @DisplayName("the encrypted bytes never contain the plaintext")
    void ciphertext_doesNotContainPlaintext() {
        byte[] decoded = Base64.getDecoder().decode(encryptor.encrypt("very-secret-value"));

        assertThat(new String(decoded, StandardCharsets.ISO_8859_1)).doesNotContain("very-secret-value");
    }

    @Test
    @DisplayName("output is nonce (12 bytes) + ciphertext + authentication tag (16 bytes)")
    void output_isNoncePlusCiphertextPlusTag() {
        byte[] decoded = Base64.getDecoder().decode(encryptor.encrypt("abc"));

        assertThat(decoded).hasSize(12 + 3 + 16);
    }

    @Test
    @DisplayName("a different master key cannot decrypt")
    void otherKey_cannotDecrypt() {
        String encrypted = encryptor.encrypt("secret");
        var other = encryptorWith(AesGcm.newKey());

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tampered ciphertext is rejected")
    void tamperedCiphertext_isRejected() {
        byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt("secret"));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("input that is not base64 is rejected with IllegalStateException")
    void notBase64_isRejected() {
        assertThatThrownBy(() -> encryptor.decrypt("not base64!!")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("input shorter than a nonce is rejected with IllegalStateException")
    void tooShortInput_isRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[5]);

        assertThatThrownBy(() -> encryptor.decrypt(tooShort)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("same wire format as AesGcm: each can read what the other wrote")
    void formatIsCompatibleWithAesGcm() {
        assertThat(AesGcm.decrypt(KEY, encryptor.encrypt("shared format"))).isEqualTo("shared format");
        assertThat(encryptor.decrypt(AesGcm.encrypt(KEY, "shared format"))).isEqualTo("shared format");
    }
}