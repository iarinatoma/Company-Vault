package com.example.template.secret.service;

import com.example.template.access.service.AccessControl;
import com.example.template.keys.service.EnvelopeEncryption;
import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.SecretResponse;
import com.example.template.secret.dto.SecretValueResponse;
import com.example.template.secret.dto.UpdateSecretRequest;
import com.example.template.secret.model.Secret;
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
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secrets of a vault (spec 4.3). Access is checked on the vault (spec 3): READ lists and reveals,
 * WRITE creates and edits, MANAGE deletes; per-secret rights come with inheritance (E6). The value
 * is encrypted with the vault's data key (spec 4.8), is only decrypted on an explicit reveal and
 * never appears in logs, URLs or error messages (spec 5). An archived vault is read-only.
 */
@Service
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private final SecretRepository secretRepository;
    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AccessControl accessControl;
    private final EnvelopeEncryption envelopeEncryption;

    public SecretService(
            SecretRepository secretRepository,
            VaultRepository vaultRepository,
            UserRepository userRepository,
            AccessControl accessControl,
            EnvelopeEncryption envelopeEncryption) {
        this.secretRepository = secretRepository;
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
        this.envelopeEncryption = envelopeEncryption;
    }

    /** The vault's secrets — metadata only, values stay masked. */
    @Transactional(readOnly = true)
    public List<SecretResponse> list(AuthPrincipal principal, UUID vaultId) {
        requireAccess(principal, vaultId, AccessLevel.READ);
        var secrets = secretRepository.findAllByVaultIdOrderByNameAsc(vaultId);
        var emails = emailsOf(secrets);
        return secrets.stream().map(secret -> toResponse(secret, emails)).toList();
    }

    /** Decrypts one value on explicit request ("reveal", spec 4.3) — the event the audit log will record. */
    @Transactional(readOnly = true)
    public SecretValueResponse reveal(AuthPrincipal principal, UUID vaultId, UUID secretId) {
        var vault = requireAccess(principal, vaultId, AccessLevel.READ);
        var secret = find(vaultId, secretId);
        log.info("Secret {} in vault {} revealed by {}", secretId, vaultId, principal.userId());
        return new SecretValueResponse(envelopeEncryption.decrypt(vault, secret.getEncryptedValue()));
    }

    @Transactional
    public SecretResponse create(AuthPrincipal principal, UUID vaultId, CreateSecretRequest request) {
        var vault = requireWritable(principal, vaultId, AccessLevel.WRITE);
        envelopeEncryption.ensureKey(vault);
        var secret = new Secret();
        secret.setVaultId(vaultId);
        secret.setName(request.name().strip());
        secret.setEncryptedValue(envelopeEncryption.encrypt(vault, request.value()));
        secret.setCreatedBy(principal.userId());
        secret.setUpdatedBy(principal.userId());
        secret = secretRepository.save(secret);
        log.info("Secret {} created in vault {} by {}", secret.requireId(), vaultId, principal.userId());
        return toResponse(secret, emailsOf(List.of(secret)));
    }

    /** Renames the secret; replaces the value only when a new one is given. */
    @Transactional
    public SecretResponse update(
            AuthPrincipal principal, UUID vaultId, UUID secretId, UpdateSecretRequest request) {
        var vault = requireWritable(principal, vaultId, AccessLevel.WRITE);
        var secret = find(vaultId, secretId);
        secret.setName(request.name().strip());
        String value = request.value();
        if (value != null && !value.isBlank()) {
            secret.setEncryptedValue(envelopeEncryption.encrypt(vault, value));
        }
        secret.setUpdatedBy(principal.userId());
        secret = secretRepository.saveAndFlush(secret);
        log.info("Secret {} in vault {} updated by {}", secretId, vaultId, principal.userId());
        return toResponse(secret, emailsOf(List.of(secret)));
    }

    /** Deleting needs MANAGE (spec 3: "Manage (share/delete)"). */
    @Transactional
    public void delete(AuthPrincipal principal, UUID vaultId, UUID secretId) {
        requireWritable(principal, vaultId, AccessLevel.MANAGE);
        secretRepository.delete(find(vaultId, secretId));
        log.info("Secret {} in vault {} deleted by {}", secretId, vaultId, principal.userId());
    }

    /** The access check comes first: without a grant the answer is 403, whether the vault exists or not. */
    private ResourceRef requireAccess(AuthPrincipal principal, UUID vaultId, AccessLevel level) {
        var vault = new ResourceRef(ResourceType.VAULT, vaultId);
        accessControl.require(principal, vault, level);
        return vault;
    }

    private ResourceRef requireWritable(AuthPrincipal principal, UUID vaultId, AccessLevel level) {
        var vault = requireAccess(principal, vaultId, level);
        boolean archived = vaultRepository
                .findById(vaultId)
                .map(Vault::isArchived)
                .orElseThrow(() -> new ApiException(ErrorCode.VAULT_NOT_FOUND));
        if (archived) {
            throw new ApiException(ErrorCode.VAULT_ARCHIVED);
        }
        return vault;
    }

    private Secret find(UUID vaultId, UUID secretId) {
        return secretRepository
                .findByIdAndVaultId(secretId, vaultId)
                .orElseThrow(() -> new ApiException(ErrorCode.SECRET_NOT_FOUND));
    }

    private Map<UUID, String> emailsOf(List<Secret> secrets) {
        var ids = secrets.stream()
                .flatMap(secret -> Stream.of(secret.getCreatedBy(), secret.getUpdatedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::requireId, User::getEmail));
    }

    private static @Nullable String emailOf(Map<UUID, String> emails, @Nullable UUID userId) {
        return userId == null ? null : emails.get(userId);
    }

    private static SecretResponse toResponse(Secret secret, Map<UUID, String> emails) {
        return new SecretResponse(
                secret.requireId(),
                secret.getVaultId(),
                secret.getName(),
                emailOf(emails, secret.getCreatedBy()),
                emailOf(emails, secret.getUpdatedBy()),
                Objects.requireNonNull(secret.getCreatedAt()),
                Objects.requireNonNull(secret.getUpdatedAt()));
    }
}