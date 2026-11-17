package com.example.template.keys.service;

import com.example.template.keys.model.DataKey;
import com.example.template.keys.repository.DataKeyRepository;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.shared.crypto.AesGcm;
import com.example.template.shared.security.ResourceRef;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envelope encryption. Every vault/project has its own random data encryption key (DEK); the
 * DEK is stored only wrapped with the master key ({@link AeadEncryptor}, key from the
 * environment). Secrets are encrypted with the DEK of their vault, so a stolen database alone
 * reveals nothing, and deleting a DEK makes the vault's secrets unreadable for good.
 *
 * <p>Usage: {@link #ensureKey} when a vault is created, {@link #encrypt}/{@link #decrypt} for its
 * secrets, {@link #deleteKey} when it is deleted. Access checks happen before, in the caller.
 */
@Service
public class EnvelopeEncryption {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEncryption.class);

    private final DataKeyRepository dataKeyRepository;
    private final AeadEncryptor masterKey;

    public EnvelopeEncryption(DataKeyRepository dataKeyRepository, AeadEncryptor masterKey) {
        this.dataKeyRepository = dataKeyRepository;
        this.masterKey = masterKey;
    }

    /** Gives the resource its own DEK; does nothing if it already has one. */
    @Transactional
    public void ensureKey(ResourceRef resource) {
        if (dataKeyRepository.existsByResourceTypeAndResourceId(resource.type(), resource.id())) {
            return;
        }
        byte[] dek = AesGcm.newKey();
        var dataKey = new DataKey();
        dataKey.setResourceType(resource.type());
        dataKey.setResourceId(resource.id());
        dataKey.setWrappedKey(masterKey.encrypt(Base64.getEncoder().encodeToString(dek)));
        dataKeyRepository.save(dataKey);
        log.info("Data key created for {} {}", resource.type(), resource.id());
    }

    /** Encrypts with the resource's DEK; returns base64 of nonce || ciphertext || tag. */
    @Transactional(readOnly = true)
    public String encrypt(ResourceRef resource, String plaintext) {
        return AesGcm.encrypt(dekOf(resource), plaintext);
    }

    /** Decrypts with the resource's DEK; throws {@code IllegalStateException} on wrong key or tampering. */
    @Transactional(readOnly = true)
    public String decrypt(ResourceRef resource, String encrypted) {
        return AesGcm.decrypt(dekOf(resource), encrypted);
    }

    /** Removes the resource's DEK — everything encrypted with it becomes unreadable. */
    @Transactional
    public void deleteKey(ResourceRef resource) {
        dataKeyRepository.deleteByResourceTypeAndResourceId(resource.type(), resource.id());
        log.info("Data key deleted for {} {}", resource.type(), resource.id());
    }

    /** Unwraps the DEK with the master key; it exists in plain form only in memory, never in logs. */
    private byte[] dekOf(ResourceRef resource) {
        var dataKey = dataKeyRepository
                .findByResourceTypeAndResourceId(resource.type(), resource.id())
                .orElseThrow(() -> new IllegalStateException(
                        "No data key for " + resource.type() + " " + resource.id()));
        return Base64.getDecoder().decode(masterKey.decrypt(dataKey.getWrappedKey()));
    }
}