package com.example.template.keys.service;

import com.example.template.shared.config.AppSecurityProperties;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Provides an exportable representation of the configured master key for the admin backup flow. */
@Service
public class MasterKeyBackupService {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyBackupService.class);

    private final AppSecurityProperties.Encryption encryption;

    public MasterKeyBackupService(AppSecurityProperties properties) {
        this.encryption = properties.encryption();
    }

    /** Returns the standard Base64 encoding accepted by the {@code ENCRYPTION_KEY} setting. */
    public String backupValue(UUID administratorId) {
        log.info("Master key backup downloaded by administrator {}", administratorId);
        return Base64.getEncoder().encodeToString(encryption.keyBytes());
    }
}
