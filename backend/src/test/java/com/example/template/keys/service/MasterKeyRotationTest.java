package com.example.template.keys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.template.keys.model.DataKey;
import com.example.template.keys.repository.DataKeyRepository;
import com.example.template.shared.config.AppSecurityProperties;
import com.example.template.shared.config.KeyRotationProperties;
import com.example.template.shared.crypto.AesGcm;
import com.example.template.shared.crypto.AesGcmEncryptor;
import com.example.template.shared.security.ResourceType;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Master key rotation with the real AES-GCM master-key encryptor in its rotation setup:
 * current key NEW_KEY, previous key OLD_KEY.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MasterKeyRotation")
class MasterKeyRotationTest {

    private static final byte[] OLD_KEY = AesGcm.newKey();
    private static final byte[] NEW_KEY = AesGcm.newKey();

    @Mock
    private DataKeyRepository dataKeyRepository;

    @Mock
    private UserRepository userRepository;

    private MasterKeyRotation rotation;

    @BeforeEach
    void setUp() {
        rotation = new MasterKeyRotation(dataKeyRepository, userRepository, rotatingMasterKey());
    }

    /** Encrypts with NEW_KEY; decrypts with NEW_KEY or, as the previous key, OLD_KEY. */
    private static AesGcmEncryptor rotatingMasterKey() {
        return new AesGcmEncryptor(
                new AppSecurityProperties(
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
                        new AppSecurityProperties.Encryption(base64(NEW_KEY))),
                new KeyRotationProperties(base64(OLD_KEY)));
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static DataKey dataKeyWrappedWith(byte[] masterKey, byte[] dek) {
        var dataKey = new DataKey();
        dataKey.setResourceType(ResourceType.VAULT);
        dataKey.setResourceId(UUID.randomUUID());
        dataKey.setWrappedKey(AesGcm.encrypt(masterKey, base64(dek)));
        return dataKey;
    }

    @Test
    @DisplayName("re-wraps every data key with the new master key; the DEK itself stays the same")
    void rotate_reWrapsDataKeysWithNewMasterKey() {
        byte[] dek = AesGcm.newKey();
        var dataKey = dataKeyWrappedWith(OLD_KEY, dek);
        when(dataKeyRepository.findAll()).thenReturn(List.of(dataKey));

        var result = rotation.rotate();

        assertThat(result.dataKeys()).isEqualTo(1);
        assertThat(AesGcm.decrypt(NEW_KEY, dataKey.getWrappedKey())).isEqualTo(base64(dek));
        assertThatThrownBy(() -> AesGcm.decrypt(OLD_KEY, dataKey.getWrappedKey()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("re-encrypts every TOTP secret with the new master key")
    void rotate_reEncryptsTotpSecrets() {
        var user = new User();
        user.setTotpSecret(AesGcm.encrypt(OLD_KEY, "JBSWY3DPEHPK3PXP"));
        when(userRepository.findAllByTotpSecretIsNotNull()).thenReturn(List.of(user));

        var result = rotation.rotate();

        assertThat(result.totpSecrets()).isEqualTo(1);
        assertThat(AesGcm.decrypt(NEW_KEY, user.getTotpSecret())).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    @DisplayName("values already under the new key stay readable — running it twice is safe")
    void rotate_twice_isSafe() {
        byte[] dek = AesGcm.newKey();
        var dataKey = dataKeyWrappedWith(NEW_KEY, dek);
        when(dataKeyRepository.findAll()).thenReturn(List.of(dataKey));

        rotation.rotate();
        rotation.rotate();

        assertThat(AesGcm.decrypt(NEW_KEY, dataKey.getWrappedKey())).isEqualTo(base64(dek));
    }

    @Test
    @DisplayName("a value under an unknown key aborts the rotation")
    void rotate_unknownKey_fails() {
        var dataKey = dataKeyWrappedWith(AesGcm.newKey(), AesGcm.newKey());
        when(dataKeyRepository.findAll()).thenReturn(List.of(dataKey));

        assertThatThrownBy(() -> rotation.rotate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("nothing to rotate: both counts are zero")
    void rotate_nothingToDo_returnsZeros() {
        var result = rotation.rotate();

        assertThat(result.dataKeys()).isZero();
        assertThat(result.totpSecrets()).isZero();
    }
}