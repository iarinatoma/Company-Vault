package com.example.template.vault.service;

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
import com.example.template.vault.dto.VaultResponse;
import com.example.template.vault.model.Vault;
import com.example.template.vault.model.VaultType;
import com.example.template.vault.repository.VaultRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.template.vault.dto.CreateProjectVaultRequest;

/**
 * Vaults: creation, the list a user may see, and the metadata of one vault. Who may use a vault is
 * decided by {@link AccessControl}; its data key is created by {@link EnvelopeEncryption}.
 * Administrators see the metadata of every vault (spec 4.7) but get no automatic access to secrets.
 */
@Service
public class VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultService.class);

    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AccessControl accessControl;
    private final EnvelopeEncryption envelopeEncryption;

    public VaultService(
            VaultRepository vaultRepository,
            UserRepository userRepository,
            AccessControl accessControl,
            EnvelopeEncryption envelopeEncryption) {
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
        this.envelopeEncryption = envelopeEncryption;
    }

    /**
     * Creates a general vault: stores it, gives it its own data key and makes the chosen owner its
     * manager. The creating admin gets no grant — unless they chose themselves as owner.
     */
    @Transactional
    public VaultResponse createGeneral(AuthPrincipal admin, CreateVaultRequest request) {
        return create(admin, VaultType.GENERAL, request.name(), request.description(), request.ownerId());
    }

    /**
     * Creates a project vault. A project owner manages their own project vault; an administrator
     * chooses its owner and, as for general vaults, gets no access unless they choose themselves.
     */
    @Transactional
    public VaultResponse createProject(AuthPrincipal principal, CreateProjectVaultRequest request) {
        UUID ownerId = principal.userId();
        if (principal.role() == Role.ADMINISTRATOR) {
            UUID chosen = request.ownerId();
            if (chosen == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "An administrator must choose the owner");
            }
            ownerId = chosen;
        }
        return create(principal, VaultType.PROJECT, request.name(), request.description(), ownerId);
    }

    /** Stores the vault, creates its data key and gives the owner MANAGE — shared by every vault type. */
    private VaultResponse create(
            AuthPrincipal creator, VaultType type, String name, @Nullable String description, UUID ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        var vault = new Vault();
        vault.setName(name.strip());
        vault.setDescription(blankToNull(description));
        vault.setType(type);
        vault.setCreatedBy(creator.userId());
        vault = vaultRepository.save(vault);

        var resource = refOf(vault);
        envelopeEncryption.ensureKey(resource);
        accessControl.grant(ownerId, resource, AccessLevel.MANAGE);
        log.info("{} vault {} created by {} with owner {}", type, vault.requireId(), creator.userId(), ownerId);
        return toResponse(vault, accessControl.levelOf(creator, resource));
    }

    /** Admins: every active vault (metadata only). Everyone else: the active vaults they can read. */
    @Transactional(readOnly = true)
    public List<VaultResponse> list(AuthPrincipal principal) {
        List<Vault> vaults;
        if (principal.role() == Role.ADMINISTRATOR) {
            vaults = vaultRepository.findAllByArchivedFalseOrderByNameAsc();
        } else {
            var readable = accessControl.resourceIdsOf(principal, ResourceType.VAULT, AccessLevel.READ);
            vaults = readable.isEmpty()
                    ? List.of()
                    : vaultRepository.findAllByIdInAndArchivedFalseOrderByNameAsc(readable);
        }
        return vaults.stream()
                .map(vault -> toResponse(vault, accessControl.levelOf(principal, refOf(vault))))
                .toList();
    }

    /** One vault's metadata. Admins may always see it; everyone else needs at least READ. */
    @Transactional(readOnly = true)
    public VaultResponse get(AuthPrincipal principal, UUID id) {
        var resource = new ResourceRef(ResourceType.VAULT, id);
        if (principal.role() != Role.ADMINISTRATOR) {
            accessControl.require(principal, resource, AccessLevel.READ);
        }
        var vault = vaultRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.VAULT_NOT_FOUND));
        return toResponse(vault, accessControl.levelOf(principal, resource));
    }

    private static ResourceRef refOf(Vault vault) {
        return new ResourceRef(ResourceType.VAULT, vault.requireId());
    }

    private static VaultResponse toResponse(Vault vault, AccessLevel level) {
        return new VaultResponse(
                vault.requireId(),
                vault.getName(),
                vault.getDescription(),
                vault.getType(),
                vault.isArchived(),
                Objects.requireNonNull(vault.getCreatedAt()),
                level);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}