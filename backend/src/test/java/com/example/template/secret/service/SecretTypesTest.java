package com.example.template.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.service.AccessControl;
import com.example.template.folder.repository.FolderRepository;
import com.example.template.keys.service.EnvelopeEncryption;
import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.CustomField;
import com.example.template.secret.dto.MoveSecretRequest;
import com.example.template.secret.dto.SecretFields;
import com.example.template.secret.dto.UpdateSecretRequest;
import com.example.template.secret.model.Secret;
import com.example.template.secret.model.SecretType;
import com.example.template.secret.repository.SecretRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Secret types (spec 4.3) and their type-specific fields. The fake data-key encryption below wraps
 * the plaintext as "enc[vault]:plaintext", so each test can see exactly what got encrypted, with
 * which vault's key, and that nothing type-specific is stored in plain text.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecretService — secret types and encrypted fields")
class SecretTypesTest {

    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final UUID OTHER_VAULT_ID = UUID.randomUUID();
    private static final ResourceRef VAULT = new ResourceRef(ResourceType.VAULT, VAULT_ID);
    private static final ResourceRef OTHER_VAULT = new ResourceRef(ResourceType.VAULT, OTHER_VAULT_ID);
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

    @BeforeEach
    void fakeDataKeyEncryption() {
        lenient().when(envelopeEncryption.encrypt(any(ResourceRef.class), anyString())).thenAnswer(invocation -> {
            ResourceRef vault = invocation.getArgument(0);
            return "enc[" + vault.id() + "]:" + invocation.getArgument(1);
        });
        lenient().when(envelopeEncryption.decrypt(any(ResourceRef.class), anyString())).thenAnswer(invocation -> {
            ResourceRef vault = invocation.getArgument(0);
            String prefix = "enc[" + vault.id() + "]:";
            String ciphertext = invocation.getArgument(1);
            if (!ciphertext.startsWith(prefix)) {
                throw new IllegalStateException("wrong data key");
            }
            return ciphertext.substring(prefix.length());
        });
        lenient().when(vaultRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Vault()));
    }

    private static String encryptedFor(ResourceRef vault, String plaintext) {
        return "enc[" + vault.id() + "]:" + plaintext;
    }

    private void savesAsIs() {
        when(secretRepository.save(any(Secret.class))).thenAnswer(invocation -> {
            Secret saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });
    }

    private Secret createAndCapture(CreateSecretRequest request) {
        savesAsIs();
        service.create(USER, VAULT_ID, request);
        var saved = ArgumentCaptor.forClass(Secret.class);
        verify(secretRepository).save(saved.capture());
        return saved.getValue();
    }

    private static Secret stored(SecretType type, String value, SecretFields fields) {
        var secret = new Secret();
        secret.setVaultId(VAULT_ID);
        secret.setName("Stored");
        secret.setType(type);
        secret.setEncryptedValue(encryptedFor(VAULT, value));
        secret.setEncryptedFields(encryptedFor(VAULT, SecretFieldsJson.write(fields)));
        ReflectionTestUtils.setField(secret, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(secret, "createdAt", Instant.now());
        ReflectionTestUtils.setField(secret, "updatedAt", Instant.now());
        return secret;
    }

    private static CreateSecretRequest create(String value, SecretType type, SecretFields fields) {
        return new CreateSecretRequest("Name", value, null, null, null, null, type, fields);
    }

    @Test
    @DisplayName("a LOGIN keeps its type in plain text and its username and URL only encrypted")
    void create_login_encryptsUsernameAndUrl() {
        var saved = createAndCapture(
                create("pa55word", SecretType.LOGIN, new SecretFields("alice", "https://mail.example.com", null)));

        assertThat(saved.getType()).isEqualTo(SecretType.LOGIN);
        assertThat(saved.getEncryptedValue()).isEqualTo(encryptedFor(VAULT, "pa55word"));
        assertThat(saved.getEncryptedFields()).startsWith("enc[" + VAULT_ID + "]:");
        assertThat(saved.getEncryptedFields())
                .contains("\"username\":\"alice\"")
                .contains("\"url\":\"https://mail.example.com\"");
    }

    @Test
    @DisplayName("without a type a secret is an API_KEY, and without fields nothing extra is stored")
    void create_withoutTypeOrFields_isPlainApiKey() {
        var saved = createAndCapture(new CreateSecretRequest("Stripe", "sk_live_123"));

        assertThat(saved.getType()).isEqualTo(SecretType.API_KEY);
        assertThat(saved.getEncryptedFields()).isNull();
    }

    @Test
    @DisplayName("username and URL belong to a LOGIN only; custom fields work for every type")
    void create_nonLogin_dropsUsernameAndUrl_keepsCustomFields() {
        var saved = createAndCapture(create(
                "token-abc",
                SecretType.API_KEY,
                new SecretFields("ignored", "https://ignored", List.of(new CustomField(" Region ", "eu-central-1")))));

        assertThat(saved.getEncryptedFields())
                .doesNotContain("ignored")
                .contains("\"name\":\"Region\"")
                .contains("\"value\":\"eu-central-1\"");
    }

    @Test
    @DisplayName("blank fields are not stored at all")
    void create_blankFields_storesNothingExtra() {
        var saved = createAndCapture(create("pa55word", SecretType.LOGIN, new SecretFields("  ", "", List.of())));

        assertThat(saved.getEncryptedFields()).isNull();
    }

    @Test
    @DisplayName("the list shows the type but never decrypts anything")
    void list_showsTypeWithoutDecrypting() {
        var secret = stored(SecretType.NOTE, "my note", SecretFields.EMPTY);
        when(secretRepository.findAllByVaultIdOrderByNameAsc(VAULT_ID)).thenReturn(List.of(secret));

        var listed = service.list(USER, VAULT_ID);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).type()).isEqualTo(SecretType.NOTE);
        verify(envelopeEncryption, never()).decrypt(any(), any());
    }

    @Test
    @DisplayName("reveal returns the value and the decrypted fields")
    void reveal_returnsValueAndFields() {
        var secret = stored(
                SecretType.LOGIN,
                "pa55word",
                new SecretFields("alice", "https://mail.example.com", List.of(new CustomField("PIN", "1234"))));
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));

        var revealed = service.reveal(USER, VAULT_ID, secret.requireId());

        assertThat(revealed.value()).isEqualTo("pa55word");
        assertThat(revealed.fields().username()).isEqualTo("alice");
        assertThat(revealed.fields().url()).isEqualTo("https://mail.example.com");
        assertThat(revealed.fields().customFields()).containsExactly(new CustomField("PIN", "1234"));
    }

    @Test
    @DisplayName("a secret stored before secret types existed reveals with empty fields")
    void reveal_withoutFields_returnsEmptyFields() {
        var secret = stored(SecretType.API_KEY, "old-value", SecretFields.EMPTY);
        secret.setEncryptedFields(null);
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));

        var revealed = service.reveal(USER, VAULT_ID, secret.requireId());

        assertThat(revealed.value()).isEqualTo("old-value");
        assertThat(revealed.fields()).isEqualTo(SecretFields.EMPTY);
    }

    @Test
    @DisplayName("an edit without fields keeps the stored fields; with fields it replaces them")
    void update_keepsOrReplacesFields() {
        var secret = stored(SecretType.LOGIN, "pa55word", new SecretFields("alice", null, List.of()));
        String fieldsBefore = secret.getEncryptedFields();
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        service.update(USER, VAULT_ID, secret.requireId(), new UpdateSecretRequest("Renamed", ""));
        assertThat(secret.getEncryptedFields()).isEqualTo(fieldsBefore);

        service.update(
                USER,
                VAULT_ID,
                secret.requireId(),
                new UpdateSecretRequest(
                        "Renamed", "", null, null, null, null, null, new SecretFields("bob", null, List.of())));
        assertThat(secret.getEncryptedFields()).contains("\"username\":\"bob\"").doesNotContain("alice");
    }

    @Test
    @DisplayName("an edit can change the type")
    void update_changesType() {
        var secret = stored(SecretType.API_KEY, "value", SecretFields.EMPTY);
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        var response = service.update(
                USER,
                VAULT_ID,
                secret.requireId(),
                new UpdateSecretRequest("Name", "", null, null, null, null, SecretType.NOTE, null));

        assertThat(secret.getType()).isEqualTo(SecretType.NOTE);
        assertThat(response.type()).isEqualTo(SecretType.NOTE);
    }

    @Test
    @DisplayName("moving to another vault re-encrypts the fields with the target vault's data key")
    void move_toOtherVault_reEncryptsFields() {
        var secret = stored(SecretType.LOGIN, "pa55word", new SecretFields("alice", null, List.of()));
        when(secretRepository.findByIdAndVaultId(secret.requireId(), VAULT_ID)).thenReturn(Optional.of(secret));
        when(secretRepository.saveAndFlush(secret)).thenReturn(secret);

        service.move(USER, VAULT_ID, secret.requireId(), new MoveSecretRequest(OTHER_VAULT_ID, null));

        assertThat(secret.getEncryptedValue()).isEqualTo(encryptedFor(OTHER_VAULT, "pa55word"));
        assertThat(secret.getEncryptedFields()).startsWith("enc[" + OTHER_VAULT_ID + "]:").contains("alice");
    }

    @Test
    @DisplayName("the fields survive the JSON round trip unchanged")
    void fieldsJson_roundTrip() {
        var fields = new SecretFields(
                "alice", "https://x.example", List.of(new CustomField("A", "1"), new CustomField("B", "")));

        assertThat(SecretFieldsJson.read(SecretFieldsJson.write(fields))).isEqualTo(fields);
    }
}