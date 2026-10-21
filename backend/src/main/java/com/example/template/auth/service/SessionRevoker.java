package com.example.template.auth.service;

import com.example.template.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token theft response: presenting a rotated (already-used) token revokes the whole
 * session family. REQUIRES_NEW so the revocation survives the rejection that follows it —
 * the other reference implementation of the rollback trap next to LoginAttemptService.
 */
@Service
public class SessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public SessionRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        var now = Instant.now();
        var active = refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId);
        active.forEach(token -> token.setRevokedAt(now));
        log.warn("Revoked refresh token family {} after reuse of a rotated token", familyId);
    }
}
