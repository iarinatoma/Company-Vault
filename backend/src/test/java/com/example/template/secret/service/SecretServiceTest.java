package com.example.template.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.service.AccessControl;
import com.example.template.keys.service.EnvelopeEncryption;
import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.UpdateSecretRequest;
import com.example.template.secret.model.Secret;
import com.example.template.secret.repository.SecretRepository;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import com.example.template.shared.security.Role;
import com.example.template.user.repository.UserRepository;
import com.example.template.vault.model.Vault;
import com.example.template.vault.repository.VaultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretService")
class SecretServiceTest {

    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final ResourceRef VAULT = new ResourceRef(ResourceType.VAULT, VAULT_ID);
    private static final AuthPrincipal USER = new AuthPrincipal(UUID.randomUUID(), "user@example.com", Role.MEMBER);

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private VaultRepository vaultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessControl accessControl;

    @Mock
    private EnvelopeEncryption envelopeEncryption;

    @InjectMocks
    private SecretService service;

    private static Secret storedSecret(String name, String encryptedValue) {
        var secret = new Secret();
        secret.setVaultId(VAULT_ID);
        secret.setName(name);
        secret.setEncryptedValue(encryptedValue);
        ReflectionTestUtils.setField(secret, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(secret, "createdAt", Instant.now());
        ReflectionTestUtils.setField(secret, "updatedAt", Instant.now());
        return secret;
    }

    private void activeVault() {
        when(vaultRepository.findById(VAULT_ID)).thenReturn(Optional.of(new Vault()));
    }

    private void denied(AccessLevel level) {
        doThrow(new ApiException(ErrorCode.FORBIDDEN)).when(accessControl).require(USER, VAULT, level);
    }

    private static ErrorCode codeOf(Throwable exception) {
        return ((ApiException) exception).errorCode();
    }

    @Test
    @DisplayName("listing needs READ and returns metadata only")
    void list_withRead_returnsMetadata() {
        when(secretRepository.findAllByVaultIdOrderByNameAsc(VAULT_ID))
                .thenReturn(List.of(storedSecret("Database", "ENCRYPTED")));

        var secrets = service.list(USER, VAULT_ID);

        verify(accessControl).require(USER, VAULT, AccessLevel.READ);
        assertThat(secrets).singleElement().satisfies(secret -> assertThat(secret.name()).isEqualTo("Database"));
        verify(envelopeEncryption, never()).decrypt(any(), any());
    }

    @Test
    @DisplayName("someone without access to the vault cannot list its secrets")
    void list_withoutAccess_isForbidden() {
        denied(AccessLevel.READ);

        assertThatThrownBy(() -> service.list(USER, VAULT_ID)).extracting(SecretServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(secretRepository, never()).findAllByVaultIdOrderByNameAsc(any());
    }

    @Test
    @DisplayName("revealing decrypts the value with the vault's data key")
    void reveal_decryptsWithVaultKey() {
        var secret = storedSecret("Database", "ENCRYPTED");
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(envelopeEncryption.decrypt(VAULT, "ENCRYPTED")).thenReturn("s3cr3t");

        assertThat(service.reveal(USER, VAULT_ID, secret.requireId()).value()).isEqualTo("s3cr3t");
    }

    @Test
    @DisplayName("a secret of another vault is not found through this vault")
    void reveal_secretOfOtherVault_isNotFound() {
        UUID foreignSecret = UUID.randomUUID();
        when(secretRepository.findByIdAndVaultId(foreignSecret, VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reveal(USER, VAULT_ID, foreignSecret)).extracting(SecretServiceTest::codeOf)
                .isEqualTo(ErrorCode.SECRET_NOT_FOUND);}



}