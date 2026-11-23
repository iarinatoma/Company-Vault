package com.example.template.folder.service;

import com.example.template.access.service.AccessControl;
import com.example.template.folder.dto.CreateFolderRequest;
import com.example.template.folder.dto.FolderResponse;
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
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import com.example.template.vault.model.Vault;
import com.example.template.vault.repository.VaultRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderService.class);

    private final FolderRepository folderRepository;
    private final SecretRepository secretRepository;
    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AccessControl accessControl;

    public FolderService(
            FolderRepository folderRepository,
            SecretRepository secretRepository,
            VaultRepository vaultRepository,
            UserRepository userRepository,
            AccessControl accessControl) {
        this.folderRepository = folderRepository;
        this.secretRepository = secretRepository;
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> list(AuthPrincipal principal, UUID vaultId) {
        requireAccess(principal, vaultId, AccessLevel.READ);
        var folders = folderRepository.findAllByVaultIdOrderByNameAsc(vaultId);
        var emails = emailsOf(folders);
        return folders.stream()
                .map(folder -> toResponse(
                        folder,
                        secretRepository.countByVaultIdAndFolderId(vaultId, folder.requireId()),
                        emails))
                .toList();
    }

    @Transactional
    public FolderResponse create(AuthPrincipal principal, UUID vaultId, CreateFolderRequest request) {
        requireWritable(principal, vaultId, AccessLevel.WRITE);
        String name = request.name().strip();
        if (folderRepository.existsByVaultIdAndNameIgnoreCase(vaultId, name)) {
            throw new ApiException(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        }

        var folder = new Folder();
        folder.setVaultId(vaultId);
        folder.setName(name);
        folder.setCreatedBy(principal.userId());
        folder.setUpdatedBy(principal.userId());
        folder = folderRepository.save(folder);

        log.info("Folder {} created in vault {} by {}", folder.requireId(), vaultId, principal.userId());
        return toResponse(folder, 0L, emailsOf(List.of(folder)));
    }

    @Transactional
    public FolderResponse update(AuthPrincipal principal, UUID vaultId, UUID folderId, UpdateFolderRequest request) {
        requireWritable(principal, vaultId, AccessLevel.WRITE);
        var folder = find(vaultId, folderId);
        String name = request.name().strip();
        if (folderRepository.existsByVaultIdAndNameIgnoreCaseAndIdNot(vaultId, name, folderId)) {
            throw new ApiException(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        }

        folder.setName(name);
        folder.setUpdatedBy(principal.userId());
        folder = folderRepository.saveAndFlush(folder);

        log.info("Folder {} in vault {} renamed by {}", folderId, vaultId, principal.userId());
        long count = secretRepository.countByVaultIdAndFolderId(vaultId, folderId);
        return toResponse(folder, count, emailsOf(List.of(folder)));
    }

    /** Deletes the folder; secrets inside become unfiled (folderId = null) in the same vault. */
    @Transactional
    public void delete(AuthPrincipal principal, UUID vaultId, UUID folderId) {
        requireWritable(principal, vaultId, AccessLevel.MANAGE);
        var folder = find(vaultId, folderId);
        secretRepository.clearFolderId(vaultId, folderId);
        folderRepository.delete(folder);
        log.info("Folder {} in vault {} deleted by {}", folderId, vaultId, principal.userId());
    }

    private void requireAccess(AuthPrincipal principal, UUID vaultId, AccessLevel level) {
        accessControl.require(principal, new ResourceRef(ResourceType.VAULT, vaultId), level);
    }

    private void requireWritable(AuthPrincipal principal, UUID vaultId, AccessLevel level) {
        requireAccess(principal, vaultId, level);
        boolean archived = vaultRepository
                .findById(vaultId)
                .map(Vault::isArchived)
                .orElseThrow(() -> new ApiException(ErrorCode.VAULT_NOT_FOUND));
        if (archived) {
            throw new ApiException(ErrorCode.VAULT_ARCHIVED);
        }
    }

    private Folder find(UUID vaultId, UUID folderId) {
        return folderRepository
                .findByIdAndVaultId(folderId, vaultId)
                .orElseThrow(() -> new ApiException(ErrorCode.FOLDER_NOT_FOUND));
    }

    private Map<UUID, String> emailsOf(List<Folder> folders) {
        var ids = folders.stream()
                .map(Folder::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::requireId, User::getEmail));
    }

    private static @Nullable String emailOf(Map<UUID, String> emails, @Nullable UUID userId) {
        return userId == null ? null : emails.get(userId);
    }

    private static FolderResponse toResponse(Folder folder, long secretCount, Map<UUID, String> emails) {
        return new FolderResponse(
                folder.requireId(),
                folder.getVaultId(),
                folder.getName(),
                secretCount,
                emailOf(emails, folder.getCreatedBy()),
                Objects.requireNonNull(folder.getCreatedAt()),
                Objects.requireNonNull(folder.getUpdatedAt()));
    }
}