package com.example.template.access.service;

import com.example.template.access.model.AccessGrant;
import com.example.template.access.repository.AccessGrantRepository;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.Role;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.template.access.dto.AccessLevelResponse;

/**
 * The central permission check for vaults, projects, folders and secrets. Every service method
 * that touches such a resource asks here first — e.g. {@code require(principal, secret, WRITE)}
 * before editing a secret — instead of deciding on its own.
 *
 * <p>Role rules on top of the stored grants:
 * <ul>
 *   <li>AUDITOR never reaches a resource: auditors read audit logs, never secret values (spec, section 3).</li>
 *   <li>ADMINISTRATOR gets no automatic access: admins manage users and groups but do not
 *       automatically see secret values (spec, section 4.7) — they need a grant like anyone else.</li>
 * </ul>
 * Inheritance (project → folder → secret) and groups come with E6 and change only {@link #levelOf}.
 */
@Service
public class AccessControl {

    private static final Logger log = LoggerFactory.getLogger(AccessControl.class);

    private final AccessGrantRepository accessGrantRepository;

    public AccessControl(AccessGrantRepository accessGrantRepository) {
        this.accessGrantRepository = accessGrantRepository;
    }

    /** The caller's effective level on a resource; no grant means NO_ACCESS. */
    @Transactional(readOnly = true)
    public AccessLevel levelOf(AuthPrincipal principal, ResourceRef resource) {
        if (principal.role() == Role.AUDITOR) {
            return AccessLevel.NO_ACCESS;
        }
        return accessGrantRepository
                .findByUserIdAndResourceTypeAndResourceId(principal.userId(), resource.type(), resource.id())
                .map(AccessGrant::getAccessLevel)
                .orElse(AccessLevel.NO_ACCESS);
    }

    /** {@link #levelOf} as a response for the frontend. */
    @Transactional(readOnly = true)
    public AccessLevelResponse describe(AuthPrincipal principal, ResourceRef resource) {
        return new AccessLevelResponse(resource.type(), resource.id(), levelOf(principal, resource));
    }

    /** Throws FORBIDDEN unless the caller has at least {@code required} on the resource. */
    @Transactional(readOnly = true)
    public void require(AuthPrincipal principal, ResourceRef resource, AccessLevel required) {
        if (!levelOf(principal, resource).includes(required)) {
            log.warn(
                    "Access denied: user {} needs {} on {} {}",
                    principal.userId(),
                    required,
                    resource.type(),
                    resource.id());
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Sets a user's level on a resource, replacing any previous one; NO_ACCESS removes the grant.
     * Building block for E4 (the creator of a vault gets MANAGE) and E6 (sharing).
     */
    @Transactional
    public void grant(UUID userId, ResourceRef resource, AccessLevel level) {
        if (level == AccessLevel.NO_ACCESS) {
            revoke(userId, resource);
            return;
        }
        var grant = accessGrantRepository
                .findByUserIdAndResourceTypeAndResourceId(userId, resource.type(), resource.id())
                .orElseGet(() -> newGrant(userId, resource));
        grant.setAccessLevel(level);
        accessGrantRepository.save(grant);
        log.info("Granted {} on {} {} to user {}", level, resource.type(), resource.id(), userId);
    }

    /** Removes a user's grant on a resource, if there is one. */
    @Transactional
    public void revoke(UUID userId, ResourceRef resource) {
        accessGrantRepository
                .findByUserIdAndResourceTypeAndResourceId(userId, resource.type(), resource.id())
                .ifPresent(grant -> {
                    accessGrantRepository.delete(grant);
                    log.info("Revoked access on {} {} from user {}", resource.type(), resource.id(), userId);
                });
    }

    private static AccessGrant newGrant(UUID userId, ResourceRef resource) {
        var grant = new AccessGrant();
        grant.setUserId(userId);
        grant.setResourceType(resource.type());
        grant.setResourceId(resource.id());
        return grant;
    }
}