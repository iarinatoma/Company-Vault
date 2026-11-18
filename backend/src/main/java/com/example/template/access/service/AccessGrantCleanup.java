package com.example.template.access.service;

import com.example.template.access.repository.AccessGrantRepository;
import com.example.template.user.event.UserDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes the access grants of a deleted user. Runs synchronously inside the deleting
 * transaction (MANDATORY), so the foreign key never sees an orphan and a rollback keeps all —
 * the same pattern as {@code UserDeletionCleanup} in the auth feature.
 */
@Component
public class AccessGrantCleanup {

    private static final Logger log = LoggerFactory.getLogger(AccessGrantCleanup.class);

    private final AccessGrantRepository accessGrantRepository;

    public AccessGrantCleanup(AccessGrantRepository accessGrantRepository) {
        this.accessGrantRepository = accessGrantRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDeleted(UserDeletedEvent event) {
        int removed = accessGrantRepository.deleteAllByUserId(event.userId());
        log.info("Removed {} access grants of deleted user {}", removed, event.userId());
    }
}