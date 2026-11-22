package com.example.template.keys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.template.keys.model.DataKey;
import com.example.template.keys.repository.DataKeyRepository;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.shared.crypto.AesGcm;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvelopeEncryptionTest {

    /** Test-only master key; in the application it comes from ENCRYPTION_KEY. */
    private static final byte[] MASTER_KEY = AesGcm.newKey();

    private static final ResourceRef VAULT_A = new ResourceRef(ResourceType.VAULT, UUID.randomUUID());
    private static final ResourceRef VAULT_B = new ResourceRef(ResourceType.VAULT, UUID.randomUUID());

    @Mock
    private DataKeyRepository dataKeyRepository;

    /** In-memory stand-in for the data_keys table. */
    private final Map<ResourceRef, DataKey> table = new HashMap<>();

    private EnvelopeEncryption envelope;

    @BeforeEach
    void setUp() {
        AeadEncryptor masterKey = new AeadEncryptor() {
            @Override
            public String encrypt(String plaintext) {
                return AesGcm.encrypt(MASTER_KEY, plaintext);
            }

            @Override
            public String decrypt(String encrypted) {
                return AesGcm.decrypt(MASTER_KEY, encrypted);
            }
        };
        envelope = new EnvelopeEncryption(dataKeyRepository, masterKey);

        lenient().when(dataKeyRepository.save(any(DataKey.class))).thenAnswer(invocation -> {
            DataKey dataKey = invocation.getArgument(0);
            table.put(new ResourceRef(dataKey.getResourceType(), dataKey.getResourceId()), dataKey);
            return dataKey;
        });
        lenient().when(dataKeyRepository.existsByResourceTypeAndResourceId(any(), any()))
                .thenAnswer(invocation ->
                        table.containsKey(new ResourceRef(invocation.getArgument(0), invocation.getArgument(1))));
        lenient().when(dataKeyRepository.findByResourceTypeAndResourceId(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        table.get(new ResourceRef(invocation.getArgument(0), invocation.getArgument(1)))));
        lenient().doAnswer(invocation ->
                        table.remove(new ResourceRef(invocation.getArgument(0), invocation.getArgument(1))))
                .when(dataKeyRepository)
                .deleteByResourceTypeAndResourceId(any(), any());
    }

    @Test
    @DisplayName("the data key is stored only wrapped with the master key")
    void ensureKey_storesOnlyWrappedKey() {
        envelope.ensureKey(VAULT_A);

        DataKey dataKey = table.get(VAULT_A);
        assertThat(dataKey).isNotNull();
        byte[] dek = Base64.getDecoder().decode(AesGcm.decrypt(MASTER_KEY, dataKey.getWrappedKey()));
        assertThat(dek).hasSize(32);
        assertThat(dataKey.getWrappedKey()).isNotEqualTo(Base64.getEncoder().encodeToString(dek));
    }

    @Test
    @DisplayName("ensureKey keeps an existing key instead of replacing it")
    void ensureKey_twice_keepsFirstKey() {
        envelope.ensureKey(VAULT_A);
        String first = table.get(VAULT_A).getWrappedKey();

        envelope.ensureKey(VAULT_A);

        assertThat(table.get(VAULT_A).getWrappedKey()).isEqualTo(first);
        verify(dataKeyRepository, times(1)).save(any(DataKey.class));
    }

    @Test
    @DisplayName("a secret encrypted for a vault decrypts back with the same vault")
    void encryptThenDecrypt_roundTrip() {
        envelope.ensureKey(VAULT_A);

        String encrypted = envelope.encrypt(VAULT_A, "api-key-xyz");

        assertThat(encrypted).doesNotContain("api-key-xyz");
        assertThat(envelope.decrypt(VAULT_A, encrypted)).isEqualTo("api-key-xyz");
    }

    @Test
    @DisplayName("each vault has its own key: vault B cannot decrypt vault A's secret")
    void otherVault_cannotDecrypt() {
        envelope.ensureKey(VAULT_A);
        envelope.ensureKey(VAULT_B);
        String encrypted = envelope.encrypt(VAULT_A, "api-key-xyz");

        assertThatThrownBy(() -> envelope.decrypt(VAULT_B, encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tampered ciphertext is rejected")
    void tamperedCiphertext_isRejected() {
        envelope.ensureKey(VAULT_A);
        byte[] raw = Base64.getDecoder().decode(envelope.encrypt(VAULT_A, "api-key-xyz"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> envelope.decrypt(VAULT_A, tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("without a data key nothing can be encrypted")
    void missingKey_fails() {
        assertThatThrownBy(() -> envelope.encrypt(VAULT_A, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No data key");
    }

    @Test
    @DisplayName("after deleting the key the vault's secrets can no longer be decrypted")
    void deleteKey_makesSecretsUnreadable() {
        envelope.ensureKey(VAULT_A);
        String encrypted = envelope.encrypt(VAULT_A, "api-key-xyz");

        envelope.deleteKey(VAULT_A);

        assertThatThrownBy(() -> envelope.decrypt(VAULT_A, encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rotating a vault's key re-encrypts its secrets so they stay readable")
    void rotateKey_reEncryptedSecretsStayReadable() {
        envelope.ensureKey(VAULT_A);
        String first = envelope.encrypt(VAULT_A, "db-password");
        String second = envelope.encrypt(VAULT_A, "api-key");

        List<String> rotated = envelope.rotateKey(VAULT_A, List.of(first, second));

        assertThat(rotated).hasSize(2);
        assertThat(envelope.decrypt(VAULT_A, rotated.get(0))).isEqualTo("db-password");
        assertThat(envelope.decrypt(VAULT_A, rotated.get(1))).isEqualTo("api-key");
    }

    @Test
    @DisplayName("after rotation the old DEK is gone: old ciphertexts no longer decrypt")
    void rotateKey_oldCiphertextsNoLongerDecrypt() {
        envelope.ensureKey(VAULT_A);
        String old = envelope.encrypt(VAULT_A, "db-password");
        String wrappedBefore = table.get(VAULT_A).getWrappedKey();

        envelope.rotateKey(VAULT_A, List.of(old));

        assertThat(table.get(VAULT_A).getWrappedKey()).isNotEqualTo(wrappedBefore);
        assertThatThrownBy(() -> envelope.decrypt(VAULT_A, old)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rotating one vault's key leaves the other vaults untouched")
    void rotateKey_otherVaultsUntouched() {
        envelope.ensureKey(VAULT_A);
        envelope.ensureKey(VAULT_B);
        String secretOfB = envelope.encrypt(VAULT_B, "wifi-code");

        envelope.rotateKey(VAULT_A, List.of());

        assertThat(envelope.decrypt(VAULT_B, secretOfB)).isEqualTo("wifi-code");
    }

    @Test
    @DisplayName("a vault without a data key cannot be rotated")
    void rotateKey_missingKey_fails() {
        assertThatThrownBy(() -> envelope.rotateKey(VAULT_A, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No data key");
    }
}