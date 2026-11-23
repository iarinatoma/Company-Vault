package com.example.template.vault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.service.AccessControl;
import com.example.template.keys.service.EnvelopeEncryption;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import com.example.template.shared.security.Role;
import com.example.template.user.repository.UserRepository;
import com.example.template.vault.dto.CreateVaultRequest;
import com.example.template.vault.model.Vault;
import com.example.template.vault.model.VaultType;
import com.example.template.vault.repository.VaultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.template.vault.dto.CreateProjectVaultRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultService")
class VaultServiceTest {

    private static final AuthPrincipal ADMIN =
            new AuthPrincipal(UUID.randomUUID(), "admin@example.com", Role.ADMINISTRATOR);
    private static final AuthPrincipal MEMBER =
            new AuthPrincipal(UUID.randomUUID(), "member@example.com", Role.MEMBER);
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock
    private VaultRepository vaultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessControl accessControl;

    @Mock
    private EnvelopeEncryption envelopeEncryption;

    @InjectMocks
    private VaultService vaultService;

    private static Vault storedVault(String name) {
        var vault = new Vault();
        vault.setName(name);
        vault.setType(VaultType.GENERAL);
        ReflectionTestUtils.setField(vault, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(vault, "createdAt", Instant.now());
        return vault;
    }

    @Test
    @DisplayName("creating a general vault stores it, creates its data key and makes the owner its manager")
    void createGeneral_storesVaultKeyAndOwnerGrant() {
        when(userRepository.existsById(OWNER_ID)).thenReturn(true);
        when(vaultRepository.save(any(Vault.class))).thenAnswer(invocation -> {
            Vault saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });
        when(accessControl.levelOf(eq(ADMIN), any())).thenReturn(AccessLevel.NO_ACCESS);

        var response = vaultService.createGeneral(
                ADMIN, new CreateVaultRequest("  IT Infrastructure ", " ", OWNER_ID));

        var savedVault = ArgumentCaptor.forClass(Vault.class);
        verify(vaultRepository).save(savedVault.capture());
        assertThat(savedVault.getValue().getType()).isEqualTo(VaultType.GENERAL);
        assertThat(savedVault.getValue().getCreatedBy()).isEqualTo(ADMIN.userId());
        assertThat(response.name()).isEqualTo("IT Infrastructure");
        assertThat(response.description()).isNull();

        var resource = new ResourceRef(ResourceType.VAULT, response.id());
        verify(envelopeEncryption).ensureKey(resource);
        verify(accessControl).grant(OWNER_ID, resource, AccessLevel.MANAGE);
        verify(accessControl, never()).grant(eq(ADMIN.userId()), any(), any());
        assertThat(response.accessLevel()).isEqualTo(AccessLevel.NO_ACCESS);
    }

    @Test
    @DisplayName("creating a personal vault stores it, creates its data key and makes its owner manager")
    void createPersonal_storesVaultKeyAndOwnerGrant() {
        when(userRepository.existsById(MEMBER.userId())).thenReturn(true);
        when(vaultRepository.save(any(Vault.class))).thenAnswer(invocation -> {
            Vault saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });
        when(accessControl.levelOf(eq(MEMBER), any())).thenReturn(AccessLevel.MANAGE);

        var response = vaultService.createPersonal(MEMBER);

        assertThat(response.type()).isEqualTo(VaultType.PERSONAL);
        assertThat(response.name()).isEqualTo("Personal vault");
        assertThat(response.accessLevel()).isEqualTo(AccessLevel.MANAGE);
        var resource = new ResourceRef(ResourceType.VAULT, response.id());
        verify(envelopeEncryption).ensureKey(resource);
        verify(accessControl).grant(MEMBER.userId(), resource, AccessLevel.MANAGE);
    }

    @Test
    @DisplayName("an unknown owner is rejected before anything is stored")
    void createGeneral_unknownOwner_isRejected() {
        when(userRepository.existsById(OWNER_ID)).thenReturn(false);

        assertThatThrownBy(() -> vaultService.createGeneral(ADMIN, new CreateVaultRequest("X", null, OWNER_ID)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(vaultRepository, never()).save(any());
        verify(envelopeEncryption, never()).ensureKey(any());
    }

    @Test
    @DisplayName("an administrator lists every active vault")
    void list_asAdmin_returnsAllVaults() {
        when(vaultRepository.findAllByArchivedFalseOrderByNameAsc())
                .thenReturn(List.of(storedVault("A"), storedVault("B")));
        when(accessControl.levelOf(eq(ADMIN), any())).thenReturn(AccessLevel.NO_ACCESS);

        assertThat(vaultService.list(ADMIN)).extracting("name").containsExactly("A", "B");
        verify(accessControl, never()).resourceIdsOf(any(), any(), any());
    }

    @Test
    @DisplayName("a member lists only the vaults they can read")
    void list_asMember_returnsOnlyReadableVaults() {
        var readable = storedVault("Team");
        when(accessControl.resourceIdsOf(MEMBER, ResourceType.VAULT, AccessLevel.READ))
                .thenReturn(Set.of(readable.requireId()));
        when(vaultRepository.findAllByIdInAndArchivedFalseOrderByNameAsc(Set.of(readable.requireId())))
                .thenReturn(List.of(readable));
        when(accessControl.levelOf(eq(MEMBER), any())).thenReturn(AccessLevel.WRITE);

        var vaults = vaultService.list(MEMBER);

        assertThat(vaults).singleElement().satisfies(vault -> {
            assertThat(vault.name()).isEqualTo("Team");
            assertThat(vault.accessLevel()).isEqualTo(AccessLevel.WRITE);
        });
        verify(vaultRepository, never()).findAllByArchivedFalseOrderByNameAsc();
    }

    @Test
    @DisplayName("a member without any grant gets an empty list")
    void list_asMemberWithoutGrants_isEmpty() {
        when(accessControl.resourceIdsOf(MEMBER, ResourceType.VAULT, AccessLevel.READ)).thenReturn(Set.of());

        assertThat(vaultService.list(MEMBER)).isEmpty();
        verify(vaultRepository, never()).findAllByIdInAndArchivedFalseOrderByNameAsc(any());
    }

    @Test
    @DisplayName("a member without READ cannot open a vault")
    void get_asMemberWithoutAccess_isForbidden() {
        UUID id = UUID.randomUUID();
        doThrow(new ApiException(ErrorCode.FORBIDDEN))
                .when(accessControl)
                .require(MEMBER, new ResourceRef(ResourceType.VAULT, id), AccessLevel.READ);

        assertThatThrownBy(() -> vaultService.get(MEMBER, id))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(vaultRepository, never()).findById(any());
    }

    @Test
    @DisplayName("an unknown vault is reported as not found")
    void get_unknownVault_isNotFound() {
        UUID id = UUID.randomUUID();
        when(vaultRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vaultService.get(ADMIN, id))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VAULT_NOT_FOUND);
    }


    @Test
    @DisplayName("a project owner creating a project vault becomes its manager")
    void createProject_asProjectOwner_ownerIsCreator() {
        var projectOwner = new AuthPrincipal(UUID.randomUUID(), "po@example.com", Role.PROJECT_OWNER);
        when(userRepository.existsById(projectOwner.userId())).thenReturn(true);
        when(vaultRepository.save(any(Vault.class))).thenAnswer(invocation -> {
            Vault saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });
        when(accessControl.levelOf(eq(projectOwner), any())).thenReturn(AccessLevel.MANAGE);

        var response = vaultService.createProject(
                projectOwner, new CreateProjectVaultRequest("Webshop", null, UUID.randomUUID()));

        assertThat(response.type()).isEqualTo(VaultType.PROJECT);
        verify(accessControl)
                .grant(projectOwner.userId(), new ResourceRef(ResourceType.VAULT, response.id()), AccessLevel.MANAGE);
    }

    @Test
    @DisplayName("an administrator must choose the owner of a project vault")
    void createProject_asAdminWithoutOwner_isRejected() {
        assertThatThrownBy(() -> vaultService.createProject(ADMIN, new CreateProjectVaultRequest("Webshop", null, null)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(vaultRepository, never()).save(any());
    }    
}