package com.example.template.vault.service;

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
import com.example.template.vault.dto.VaultMemberResponse;
import com.example.template.vault.repository.VaultRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may use a vault (spec 3 and 4.4): members with MANAGE add, change and remove members;
 * administrators may do the same on any vault to recover access (spec 4.8), which is logged.
 * A vault always keeps at least one MANAGE member, and auditors never get access to secrets.
 */
@Service
public class VaultMemberService {

    private static final Logger log = LoggerFactory.getLogger(VaultMemberService.class);

    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AccessControl accessControl;

    public VaultMemberService(
            VaultRepository vaultRepository, UserRepository userRepository, AccessControl accessControl) {
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
    }

    /** "Who has access to this vault?" — sorted by e-mail. */
    @Transactional(readOnly = true)
    public List<VaultMemberResponse> list(AuthPrincipal principal, UUID vaultId) {
        var vault = requireMemberManagement(principal, vaultId);
        var members = accessControl.membersOf(vault);
        Map<UUID, String> emails = emailsOf(members);
        return members.stream()
                .map(member -> new VaultMemberResponse(
                        member.userId(), emails.getOrDefault(member.userId(), ""), member.level()))
                .sorted(Comparator.comparing(VaultMemberResponse::email))
                .toList();
    }

    /** Adds a user (found by e-mail) with the given level, or changes their level if already a member. */
    @Transactional
    public VaultMemberResponse add(AuthPrincipal principal, UUID vaultId, AddVaultMemberRequest request) {
        var vault = requireMemberManagement(principal, vaultId);
        var user = userRepository
                .findByEmail(request.email().strip())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return setLevel(principal, vault, user, request.accessLevel());
    }

    /** Changes a member's level; NO_ACCESS removes them. */
    @Transactional
    public VaultMemberResponse update(
            AuthPrincipal principal, UUID vaultId, UUID userId, UpdateVaultMemberRequest request) {
        var vault = requireMemberManagement(principal, vaultId);
        var user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return setLevel(principal, vault, user, request.accessLevel());
    }

    /** Removes a member from the vault — immediately (spec 4.4: "revoke access immediately"). */
    @Transactional
    public void remove(AuthPrincipal principal, UUID vaultId, UUID userId) {
        var vault = requireMemberManagement(principal, vaultId);
        ensureNotLastManager(vault, userId, AccessLevel.NO_ACCESS);
        accessControl.revoke(userId, vault);
        log.info("User {} removed from vault {} by {}", userId, vaultId, principal.userId());
    }

    private VaultMemberResponse setLevel(AuthPrincipal principal, ResourceRef vault, User user, AccessLevel level) {
        if (user.getRole() == Role.AUDITOR && level != AccessLevel.NO_ACCESS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Auditors never get access to secrets");
        }
        ensureNotLastManager(vault, user.requireId(), level);
        accessControl.grant(user.requireId(), vault, level);
        log.info("User {} set to {} on vault {} by {}", user.requireId(), level, vault.id(), principal.userId());
        return new VaultMemberResponse(user.requireId(), user.getEmail(), level);
    }

    /** Managers of the vault — and administrators, to recover access (logged as a warning for the audit trail). */
    private ResourceRef requireMemberManagement(AuthPrincipal principal, UUID vaultId) {
        var vault = new ResourceRef(ResourceType.VAULT, vaultId);
        if (principal.role() == Role.ADMINISTRATOR) {
            if (!vaultRepository.existsById(vaultId)) {
                throw new ApiException(ErrorCode.VAULT_NOT_FOUND);
            }
            if (!accessControl.levelOf(principal, vault).includes(AccessLevel.MANAGE)) {
                log.warn("Administrator {} manages the members of vault {} (access recovery)", principal.userId(), vaultId);
            }
            return vault;
        }
        accessControl.require(principal, vault, AccessLevel.MANAGE);
        return vault;
    }

    /** Refuses to demote or remove the only member with MANAGE — the vault would have no manager left. */
    private void ensureNotLastManager(ResourceRef vault, UUID userId, AccessLevel newLevel) {
        if (newLevel == AccessLevel.MANAGE) {
            return;
        }
        var managers = accessControl.membersOf(vault).stream()
                .filter(member -> member.level() == AccessLevel.MANAGE)
                .map(ResourceMember::userId)
                .toList();
        if (managers.size() == 1 && managers.contains(userId)) {
            throw new ApiException(ErrorCode.LAST_MANAGER);
        }
    }

    private Map<UUID, String> emailsOf(List<ResourceMember> members) {
        var ids = members.stream().map(ResourceMember::userId).toList();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::requireId, User::getEmail));
    }
}