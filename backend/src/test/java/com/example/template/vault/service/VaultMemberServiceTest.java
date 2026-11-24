package com.example.template.vault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.dto.ResourceMember;
import com.example.template.access.service.AccessControl;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import com.example.template.shared.security.Role;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import com.example.template.vault.dto.AddVaultMemberRequest;
import com.example.template.vault.dto.UpdateVaultMemberRequest;
import com.example.template.vault.repository.VaultRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.template.vault.model.Vault;
import com.example.template.vault.model.VaultType;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultMemberService")
class VaultMemberServiceTest {

    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final ResourceRef VAULT = new ResourceRef(ResourceType.VAULT, VAULT_ID);
    private static final AuthPrincipal MANAGER =
            new AuthPrincipal(UUID.randomUUID(), "manager@example.com", Role.MEMBER);
    private static final AuthPrincipal ADMIN =
            new AuthPrincipal(UUID.randomUUID(), "admin@example.com", Role.ADMINISTRATOR);

    @Mock
    private VaultRepository vaultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessControl accessControl;

    @InjectMocks
    private VaultMemberService service;

    private static User user(UUID id, String email, Role role) {
        var user = new User();
        user.setEmail(email);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("a manager sees who has access, sorted by e-mail")
    void list_asManager_returnsMembersByEmail() {
        var bob = user(UUID.randomUUID(), "bob@example.com", Role.MEMBER);
        var ana = user(UUID.randomUUID(), "ana@example.com", Role.MEMBER);
        when(accessControl.membersOf(VAULT)).thenReturn(List.of(
                new ResourceMember(bob.requireId(), AccessLevel.READ),
                new ResourceMember(ana.requireId(), AccessLevel.MANAGE)));
        when(userRepository.findAllById(any())).thenReturn(List.of(bob, ana));

        var members = service.list(MANAGER, VAULT_ID);

        verify(accessControl).require(MANAGER, VAULT, AccessLevel.MANAGE);
        assertThat(members).extracting("email").containsExactly("ana@example.com", "bob@example.com");
    }

    @Test
    @DisplayName("someone without MANAGE cannot see or change the members")
    void list_withoutManage_isForbidden() {
        doThrow(new ApiException(ErrorCode.FORBIDDEN)).when(accessControl).require(MANAGER, VAULT, AccessLevel.MANAGE);

        assertThatThrownBy(() -> service.list(MANAGER, VAULT_ID))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("an administrator gets 404 for an unknown vault")
    void admin_unknownVault_isNotFound() {
        when(vaultRepository.existsById(VAULT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.list(ADMIN, VAULT_ID))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VAULT_NOT_FOUND);
    }

    @Test
    @DisplayName("a manager adds a colleague by e-mail with the chosen level")
    void add_byEmail_grantsLevel() {
        var colleague = user(UUID.randomUUID(), "colleague@example.com", Role.MEMBER);
        when(userRepository.findByEmail("colleague@example.com")).thenReturn(Optional.of(colleague));

        var member = service.add(MANAGER, VAULT_ID, new AddVaultMemberRequest(" colleague@example.com ", AccessLevel.WRITE));

        verify(accessControl).grant(colleague.requireId(), VAULT, AccessLevel.WRITE);
        assertThat(member.accessLevel()).isEqualTo(AccessLevel.WRITE);
    }

    @Test
    @DisplayName("an unknown e-mail is rejected and nothing is granted")
    void add_unknownEmail_isRejected() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(MANAGER, VAULT_ID, new AddVaultMemberRequest("nobody@example.com", AccessLevel.READ)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(accessControl, never()).grant(any(), any(), any());
    }

    @Test
    @DisplayName("an auditor cannot be given access to secrets")
    void add_auditor_isRejected() {
        var auditor = user(UUID.randomUUID(), "auditor@example.com", Role.AUDITOR);
        when(userRepository.findByEmail("auditor@example.com")).thenReturn(Optional.of(auditor));

        assertThatThrownBy(() -> service.add(MANAGER, VAULT_ID, new AddVaultMemberRequest("auditor@example.com", AccessLevel.READ)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(accessControl, never()).grant(any(), any(), any());
    }

    @Test
    @DisplayName("the last manager cannot be demoted")
    void update_lastManager_isRejected() {
        var manager = user(MANAGER.userId(), "manager@example.com", Role.MEMBER);
        when(userRepository.findById(manager.requireId())).thenReturn(Optional.of(manager));
        when(accessControl.membersOf(VAULT)).thenReturn(List.of(new ResourceMember(manager.requireId(), AccessLevel.MANAGE)));

        assertThatThrownBy(() -> service.update(MANAGER, VAULT_ID, manager.requireId(), new UpdateVaultMemberRequest(AccessLevel.READ)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.LAST_MANAGER);
        verify(accessControl, never()).grant(any(), any(), any());
    }

    @Test
    @DisplayName("the last manager cannot be removed")
    void remove_lastManager_isRejected() {
        when(accessControl.membersOf(VAULT)).thenReturn(List.of(new ResourceMember(MANAGER.userId(), AccessLevel.MANAGE)));

        assertThatThrownBy(() -> service.remove(MANAGER, VAULT_ID, MANAGER.userId()))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.LAST_MANAGER);
        verify(accessControl, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("a member is removed immediately when another manager remains")
    void remove_member_revokesAccess() {
        UUID other = UUID.randomUUID();
        when(accessControl.membersOf(VAULT)).thenReturn(List.of(
                new ResourceMember(MANAGER.userId(), AccessLevel.MANAGE),
                new ResourceMember(other, AccessLevel.WRITE)));

        service.remove(MANAGER, VAULT_ID, other);

        verify(accessControl).revoke(other, VAULT);
    }

    @Test
    @DisplayName("an administrator can recover access to a vault they do not manage")
    void admin_recoversAccess() {
        var newOwner = user(UUID.randomUUID(), "new-owner@example.com", Role.MEMBER);
        when(vaultRepository.existsById(VAULT_ID)).thenReturn(true);
        when(accessControl.levelOf(ADMIN, VAULT)).thenReturn(AccessLevel.NO_ACCESS);
        when(userRepository.findByEmail("new-owner@example.com")).thenReturn(Optional.of(newOwner));

        service.add(ADMIN, VAULT_ID, new AddVaultMemberRequest("new-owner@example.com", AccessLevel.MANAGE));

        verify(accessControl).grant(newOwner.requireId(), VAULT, AccessLevel.MANAGE);
        verify(accessControl, never()).require(any(), any(), any());
    }

    private static Vault personalVault() {
        var vault = new Vault();
        vault.setName("Personal vault");
        vault.setType(VaultType.PERSONAL);
        ReflectionTestUtils.setField(vault, "id", VAULT_ID);
        return vault;
    }

    @Test
    @DisplayName("the owner cannot share their personal vault")
    void add_toPersonalVault_isRejected() {
        var colleague = user(UUID.randomUUID(), "colleague@example.com", Role.MEMBER);
        when(userRepository.findByEmail("colleague@example.com")).thenReturn(Optional.of(colleague));
        when(vaultRepository.findById(VAULT_ID)).thenReturn(Optional.of(personalVault()));

        assertThatThrownBy(() -> service.add(MANAGER, VAULT_ID, new AddVaultMemberRequest("colleague@example.com", AccessLevel.READ)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(accessControl, never()).grant(any(), any(), any());
    }

    @Test
    @DisplayName("an administrator cannot add themselves to someone's personal vault")
    void admin_addSelfToPersonalVault_isRejected() {
        var admin = user(ADMIN.userId(), ADMIN.email(), Role.ADMINISTRATOR);
        when(vaultRepository.existsById(VAULT_ID)).thenReturn(true);
        when(accessControl.levelOf(ADMIN, VAULT)).thenReturn(AccessLevel.NO_ACCESS);
        when(userRepository.findByEmail(ADMIN.email())).thenReturn(Optional.of(admin));
        when(vaultRepository.findById(VAULT_ID)).thenReturn(Optional.of(personalVault()));

        assertThatThrownBy(() -> service.add(ADMIN, VAULT_ID, new AddVaultMemberRequest(ADMIN.email(), AccessLevel.READ)))
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(accessControl, never()).grant(any(), any(), any());
    }
}