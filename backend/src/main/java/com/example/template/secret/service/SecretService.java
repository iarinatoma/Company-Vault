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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    @Transactional(readOnly = true)
    public List<SecretResponse> list(AuthPrincipal principal, UUID vaultId) {
        return list(principal, vaultId, null, null);
    }

    /**
     * Lists secrets of a vault with optional metadata-only search/filtering (spec 4.3).
     * Searches only plain-text name and tags — never decrypts secret values.
     */
    @Transactional(readOnly = true)
    public List<SecretResponse> list(
            AuthPrincipal principal,
            UUID vaultId,
            @Nullable String query,
            @Nullable String tag) {
        requireAccess(principal, vaultId, AccessLevel.READ);
        String normalizedQuery = normalizeFilter(query);
        String normalizedTag = normalizeFilter(tag);

        var filtered = secretRepository.findAllByVaultIdOrderByNameAsc(vaultId).stream()
                .filter(secret -> matchesQuery(secret, normalizedQuery))
                .filter(secret -> matchesTag(secret, normalizedTag))
                .toList();

        var emails = emailsOf(filtered);
        return filtered.stream().map(secret -> toResponse(secret, emails)).toList();
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
        secret.setDescription(blankToNull(request.description()));
        secret.setTags(serializeTags(request.tags()));
        secret.setExpiresAt(request.expiresAt());
        secret.setEncryptedValue(envelopeEncryption.encrypt(vault, request.value()));
        secret.setCreatedBy(principal.userId());
        secret.setUpdatedBy(principal.userId());
        secret = secretRepository.save(secret);

        log.info("Secret {} created in vault {} by {}", secret.requireId(), vaultId, principal.userId());
        return toResponse(secret, emailsOf(List.of(secret)));
    }

    /** Updates metadata and, if {@code value} is non-blank, re-encrypts the new value. */
    @Transactional
    public SecretResponse update(
            AuthPrincipal principal, UUID vaultId, UUID secretId, UpdateSecretRequest request) {
        var vault = requireWritable(principal, vaultId, AccessLevel.WRITE);
        var secret = find(vaultId, secretId);

        secret.setName(request.name().strip());
        secret.setDescription(blankToNull(request.description()));
        secret.setTags(serializeTags(request.tags()));
        secret.setExpiresAt(request.expiresAt());

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

    private static boolean matchesQuery(Secret secret, @Nullable String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        if (secret.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        return parseTags(secret.getTags()).stream().anyMatch(t -> t.contains(normalizedQuery));
    }

    private static boolean matchesTag(Secret secret, @Nullable String normalizedTag) {
        if (normalizedTag == null) {
            return true;
        }
        return parseTags(secret.getTags()).contains(normalizedTag);
    }

    private static @Nullable String normalizeFilter(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    private static @Nullable String serializeTags(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .map(t -> t.replace(",", ""))
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> parseTags(@Nullable String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serialized.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
                secret.getDescription(),
                parseTags(secret.getTags()),
                secret.getExpiresAt(),
                emailOf(emails, secret.getCreatedBy()),
                emailOf(emails, secret.getUpdatedBy()),
                Objects.requireNonNull(secret.getCreatedAt()),
                Objects.requireNonNull(secret.getUpdatedAt()));
    }
}