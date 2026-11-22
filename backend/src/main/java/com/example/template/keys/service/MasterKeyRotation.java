package com.example.template.keys.service;

import com.example.template.keys.dto.KeyRotationResponse;
import com.example.template.keys.repository.DataKeyRepository;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Master key rotation. Only two kinds of values are encrypted directly with the master key:
 * every vault's wrapped DEK and every TOTP secret. Rotation re-encrypts exactly those with the
 * current key; the secrets themselves are untouched, because they are encrypted with the DEKs,
 * which stay the same — that is the point of envelope encryption.
 *
 * <p>One transaction: if any value opens with neither the current nor the previous key, nothing
 * is changed and the previous key must stay configured.
 */
@Service
public class MasterKeyRotation {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyRotation.class);

    private final DataKeyRepository dataKeyRepository;
    private final UserRepository userRepository;
    private final AeadEncryptor masterKey;

    public MasterKeyRotation(
            DataKeyRepository dataKeyRepository, UserRepository userRepository, AeadEncryptor masterKey) {
        this.dataKeyRepository = dataKeyRepository;
        this.userRepository = userRepository;
        this.masterKey = masterKey;
    }

    @Transactional
    public KeyRotationResponse rotate() {
        int dataKeys = 0;
        for (var dataKey : dataKeyRepository.findAll()) {
            dataKey.setWrappedKey(masterKey.reEncrypt(dataKey.getWrappedKey()));
            dataKeys++;
        }

        int totpSecrets = 0;
        for (var user : userRepository.findAllByTotpSecretIsNotNull()) {
            String secret = user.getTotpSecret();
            if (secret == null) {
                continue;
            }
            user.setTotpSecret(masterKey.reEncrypt(secret));
            totpSecrets++;
        }

        // Counts only — never key material.
        log.info("Master key rotation: {} data keys and {} TOTP secrets re-encrypted", dataKeys, totpSecrets);
        return new KeyRotationResponse(dataKeys, totpSecrets);
    }
}