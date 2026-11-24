package com.example.template.folder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.service.AccessControl;
import com.example.template.folder.dto.CreateFolderRequest;
import com.example.template.folder.dto.UpdateFolderRequest;
import com.example.template.folder.model.Folder;
import com.example.template.folder.repository.FolderRepository;
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
@DisplayName("FolderService")
class FolderServiceTest {

    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final ResourceRef VAULT = new ResourceRef(ResourceType.VAULT, VAULT_ID);
    private static final AuthPrincipal USER = new AuthPrincipal(UUID.randomUUID(), "user@example.com", Role.MEMBER);

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private VaultRepository vaultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessControl accessControl;

    @InjectMocks
    private FolderService service;

    private static Folder storedFolder(String name) {
        var folder = new Folder();
        folder.setVaultId(VAULT_ID);
        folder.setName(name);
        ReflectionTestUtils.setField(folder, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(folder, "createdAt", Instant.now());
        ReflectionTestUtils.setField(folder, "updatedAt", Instant.now());
        return folder;
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
    @DisplayName("listing folders needs READ and includes secret counts")
    void list_withRead_returnsFoldersAndCounts() {
        var folder = storedFolder("Production");
        when(folderRepository.findAllByVaultIdOrderByNameAsc(VAULT_ID)).thenReturn(List.of(folder));
        when(secretRepository.countByVaultIdAndFolderId(VAULT_ID, folder.requireId())).thenReturn(3L);

        var folders = service.list(USER, VAULT_ID);

        verify(accessControl).require(USER, VAULT, AccessLevel.READ);
        assertThat(folders).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Production");
            assertThat(item.secretCount()).isEqualTo(3L);
        });
    }

    @Test
    @DisplayName("creating trims name and rejects case-insensitive duplicates in the same vault")
    void create_trimsAndRejectsDuplicateName() {
        activeVault();
        when(folderRepository.existsByVaultIdAndNameIgnoreCase(VAULT_ID, "Backend")).thenReturn(false);
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        var created = service.create(USER, VAULT_ID, new CreateFolderRequest("  Backend  "));

        var captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Backend");
        assertThat(created.secretCount()).isZero();

        when(folderRepository.existsByVaultIdAndNameIgnoreCase(VAULT_ID, "backend")).thenReturn(true);
        assertThatThrownBy(() -> service.create(USER, VAULT_ID, new CreateFolderRequest("backend")))
                .extracting(FolderServiceTest::codeOf)
                .isEqualTo(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("a reader cannot create folders and an archived vault is read-only")
    void create_withoutWriteOrArchived_isRejected() {
        denied(AccessLevel.WRITE);
        assertThatThrownBy(() -> service.create(USER, VAULT_ID, new CreateFolderRequest("Ops")))
                .extracting(FolderServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);

        var archived = new Vault();
        archived.setArchived(true);
        var otherVaultId = UUID.randomUUID();
        when(vaultRepository.findById(otherVaultId)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.create(USER, otherVaultId, new CreateFolderRequest("Ops")))
                .extracting(FolderServiceTest::codeOf)
                .isEqualTo(ErrorCode.VAULT_ARCHIVED);
    }

    @Test
    @DisplayName("renaming updates the folder name")
    void update_renamesFolder() {
        activeVault();
        var folder = storedFolder("Old");
        when(folderRepository.findByIdAndVaultId(folder.requireId(), VAULT_ID)).thenReturn(Optional.of(folder));
        when(folderRepository.existsByVaultIdAndNameIgnoreCaseAndIdNot(VAULT_ID, "New", folder.requireId()))
                .thenReturn(false);
        when(folderRepository.saveAndFlush(folder)).thenReturn(folder);

        var updated = service.update(USER, VAULT_ID, folder.requireId(), new UpdateFolderRequest("New"));

        assertThat(updated.name()).isEqualTo("New");
    }

    @Test
    @DisplayName("deleting a folder needs MANAGE and moves its secrets to the vault root")
    void delete_withManage_clearsFolderIdAndDeletes() {
        activeVault();
        var folder = storedFolder("Legacy");
        when(folderRepository.findByIdAndVaultId(folder.requireId(), VAULT_ID)).thenReturn(Optional.of(folder));

        service.delete(USER, VAULT_ID, folder.requireId());

        verify(accessControl).require(USER, VAULT, AccessLevel.MANAGE);
        verify(secretRepository).clearFolderId(VAULT_ID, folder.requireId());
        verify(folderRepository).delete(folder);
    }

    @Test
    @DisplayName("a writer cannot delete a folder")
    void delete_withoutManage_isForbidden() {
        denied(AccessLevel.MANAGE);

        assertThatThrownBy(() -> service.delete(USER, VAULT_ID, UUID.randomUUID()))
                .extracting(FolderServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(folderRepository, never()).delete(any());
    }
}