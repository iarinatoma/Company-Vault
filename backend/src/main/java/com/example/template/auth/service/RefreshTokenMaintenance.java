package com.example.template.auth.service;

import com.example.template.auth.repository.RefreshTokenRepository;
import com.example.template.shared.config.AppSecurityProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges refresh-token rows once they are useless: past their expiry plus a grace window.
 * Rotated-but-unexpired rows must stay — presenting one is the theft signal that revokes the
 * whole family, so the grace window is the forensic tail of that detection, not a convenience.
 * Without this job the table grows with every rotation, unbounded.
 */
@Service
public class RefreshTokenMaintenance {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenMaintenance.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppSecurityProperties properties;

    public RefreshTokenMaintenance(RefreshTokenRepository refreshTokenRepository, AppSecurityProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    /** Daily at 04:00 — the schedule is not a tunable knob; the retention window is (purge-grace). */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpired() {
        var cutoff = Instant.now().minus(properties.refreshToken().purgeGrace());
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} refresh tokens expired before {}", deleted, cutoff);
        }
    }
}
