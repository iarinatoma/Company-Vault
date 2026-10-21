package com.example.template.auth.service;

import com.example.template.auth.repository.RecoveryCodeRepository;
import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.user.event.UserDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes the auth data of a deleted user. Runs synchronously inside the deleting
 * transaction (MANDATORY), so the foreign keys never see an orphan and a rollback keeps all.
 */
@Component
public class UserDeletionCleanup {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionCleanup.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;

    public UserDeletionCleanup(
            RefreshTokenRepository refreshTokenRepository, RecoveryCodeRepository recoveryCodeRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDeleted(UserDeletedEvent event) {
        int tokens = refreshTokenRepository.deleteAllByUserId(event.userId());
        int codes = recoveryCodeRepository.deleteAllByUserId(event.userId());
        log.info("Removed {} refresh tokens and {} recovery codes of deleted user {}", tokens, codes, event.userId());
    }
}
