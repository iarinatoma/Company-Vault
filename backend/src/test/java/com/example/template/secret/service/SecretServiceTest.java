package com.example.template.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.service.AccessControl;
import com.example.template.folder.repository.FolderRepository;
import com.example.template.keys.service.EnvelopeEncryption;
import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.MoveSecretRequest;
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
    private FolderRepository folderRepository;

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
                .isEqualTo(ErrorCode.SECRET_NOT_FOUND);
    }

    @Test
    @DisplayName("creating encrypts the value before it is stored and records the owner")
    void create_storesOnlyTheEncryptedValue() {
        activeVault();
        when(envelopeEncryption.encrypt(VAULT, "s3cr3t")).thenReturn("ENCRYPTED");
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        service.create(USER, VAULT_ID, new CreateSecretRequest(" Database ", "s3cr3t"));

        verify(envelopeEncryption).ensureKey(VAULT);
        var saved = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(saved.capture());
        assertThat(saved.getValue().getEncryptedValue()).isEqualTo("ENCRYPTED").doesNotContain("s3cr3t");
        assertThat(saved.getValue().getName()).isEqualTo("Database");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(USER.userId());
    }

    @Test
    @DisplayName("creating with metadata stores folder, description, normalized tags and expiry")
    void create_withMetadata_storesAllFields() {
        activeVault();
        UUID folderId = UUID.randomUUID();
        when(folderRepository.existsByIdAndVaultId(folderId, VAULT_ID)).thenReturn(true);
        when(envelopeEncryption.encrypt(VAULT, "s3cr3t")).thenReturn("ENCRYPTED");
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        var response = service.create(
                USER,
                VAULT_ID,
                new CreateSecretRequest(
                        "Prod DB", "s3cr3t", folderId, "Primary Postgres", List.of("Prod", "AWS", "prod"), expiry));

        assertThat(response.folderId()).isEqualTo(folderId);
        assertThat(response.description()).isEqualTo("Primary Postgres");
        assertThat(response.tags()).containsExactly("prod", "aws");
        assertThat(response.expiresAt()).isEqualTo(expiry);
    }

    @Test
    @DisplayName("searching filters by plaintext name and tags without decrypting anything")
    void list_filtersByQueryTagAndFolder_withoutDecrypting() {
        UUID folderA = UUID.randomUUID();
        var s1 = storedSecret("Postgres Primary", "ENC_1");
        s1.setFolderId(folderA);
        s1.setTags("prod,db");

        var s2 = storedSecret("Stripe Webhook", "ENC_2");
        s2.setTags("prod,payments");

        when(secretRepository.findAllByVaultIdOrderByNameAsc(VAULT_ID)).thenReturn(List.of(s1, s2));

        var byTag = service.list(USER, VAULT_ID, null, "PAYMENTS", null, false);
        assertThat(byTag).singleElement().satisfies(s -> assertThat(s.name()).isEqualTo("Stripe Webhook"));

        var byQueryAndFolder = service.list(USER, VAULT_ID, "db", null, folderA, false);
        assertThat(byQueryAndFolder).singleElement().satisfies(s -> assertThat(s.name()).isEqualTo("Postgres Primary"));

        var rootOnly = service.list(USER, VAULT_ID, null, null, null, true);
        assertThat(rootOnly).singleElement().satisfies(s -> assertThat(s.name()).isEqualTo("Stripe Webhook"));

        verify(envelopeEncryption, never()).decrypt(any(), any());
    }

    @Test
    @DisplayName("moving between folders in the same vault updates folderId without re-encryption")
    void move_withinSameVault_doesNotReEncrypt() {
        activeVault();
        UUID targetFolderId = UUID.randomUUID();
        when(folderRepository.existsByIdAndVaultId(targetFolderId, VAULT_ID)).thenReturn(true);

        var secret = storedSecret("API Key", "ENCRYPTED_WITH_VAULT_KEY");
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        var moved = service.move(USER, VAULT_ID, secret.requireId(), new MoveSecretRequest(null, targetFolderId));

        assertThat(moved.folderId()).isEqualTo(targetFolderId);
        assertThat(secret.getEncryptedValue()).isEqualTo("ENCRYPTED_WITH_VAULT_KEY");
        verify(envelopeEncryption, never()).decrypt(any(), any());
        verify(envelopeEncryption, never()).encrypt(any(), any());
    }

    @Test
    @DisplayName("moving to another vault decrypts with the old DEK and encrypts with the new DEK")
    void move_acrossVaults_reEncryptsWithTargetVaultKey() {
        UUID targetVaultId = UUID.randomUUID();
        ResourceRef targetVaultRef = new ResourceRef(ResourceType.VAULT, targetVaultId);
        UUID targetFolderId = UUID.randomUUID();

        activeVault();
        when(vaultRepository.findById(targetVaultId)).thenReturn(Optional.of(new Vault()));
        when(folderRepository.existsByIdAndVaultId(targetFolderId, targetVaultId)).thenReturn(true);

        var secret = storedSecret("Shared Token", "OLD_CIPHERTEXT");
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(envelopeEncryption.decrypt(VAULT, "OLD_CIPHERTEXT")).thenReturn("plain-token");
        when(envelopeEncryption.encrypt(targetVaultRef, "plain-token")).thenReturn("NEW_CIPHERTEXT");
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        var moved = service.move(
                USER, VAULT_ID, secret.requireId(), new MoveSecretRequest(targetVaultId, targetFolderId));

        verify(accessControl).require(USER, VAULT, AccessLevel.MANAGE);
        verify(accessControl).require(USER, targetVaultRef, AccessLevel.WRITE);
        verify(envelopeEncryption).ensureKey(targetVaultRef);
        assertThat(moved.vaultId()).isEqualTo(targetVaultId);
        assertThat(moved.folderId()).isEqualTo(targetFolderId);
        assertThat(secret.getEncryptedValue()).isEqualTo("NEW_CIPHERTEXT");
    }

    @Test
    @DisplayName("a reader cannot create secrets")
    void create_withReadOnly_isForbidden() {
        denied(AccessLevel.WRITE);

        assertThatThrownBy(() -> service.create(USER, VAULT_ID, new CreateSecretRequest("X", "v")))
                .extracting(SecretServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(secretRepository, never()).save(any());
    }

    @Test
    @DisplayName("an archived vault is read-only")
    void create_inArchivedVault_isRejected() {
        var archived = new Vault();
        archived.setArchived(true);
        when(vaultRepository.findById(VAULT_ID)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.create(USER, VAULT_ID, new CreateSecretRequest("X", "v")))
                .extracting(SecretServiceTest::codeOf)
                .isEqualTo(ErrorCode.VAULT_ARCHIVED);
        verify(secretRepository, never()).save(any());
    }

    @Test
    @DisplayName("editing without a new value renames but keeps the stored value")
    void update_withoutValue_keepsEncryptedValue() {
        activeVault();
        var secret = storedSecret("Old", "ENCRYPTED");
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        var response = service.update(USER, VAULT_ID, secret.requireId(), new UpdateSecretRequest("New", ""));

        assertThat(response.name()).isEqualTo("New");
        assertThat(secret.getEncryptedValue()).isEqualTo("ENCRYPTED");
        verify(envelopeEncryption, never()).encrypt(any(), any());
    }

    @Test
    @DisplayName("editing with a new value encrypts it again")
    void update_withValue_reEncrypts() {
        activeVault();
        var secret = storedSecret("Database", "OLD");
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(envelopeEncryption.encrypt(VAULT, "n3w")).thenReturn("NEW");
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        service.update(USER, VAULT_ID, secret.requireId(), new UpdateSecretRequest("Database", "n3w"));

        assertThat(secret.getEncryptedValue()).isEqualTo("NEW");
        assertThat(secret.getUpdatedBy()).isEqualTo(USER.userId());
    }

    @Test
    @DisplayName("deleting needs MANAGE — a writer cannot delete")
    void delete_withoutManage_isForbidden() {
        denied(AccessLevel.MANAGE);

        assertThatThrownBy(() -> service.delete(USER, VAULT_ID, UUID.randomUUID()))
                .extracting(SecretServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(secretRepository, never()).delete(any());
    }
}