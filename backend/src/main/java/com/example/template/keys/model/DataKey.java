package com.example.template.keys.model;

import com.example.template.shared.model.BaseEntity;
import com.example.template.shared.security.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The data encryption key (DEK) of one vault/project, stored only wrapped: AES-256-GCM
 * encrypted with the master key. The plain DEK exists only in memory while encrypting or
 * decrypting. At most one row per resource (unique constraint in Liquibase).
 */
@Entity
@Table(name = "data_keys")
@Getter
@Setter
@NoArgsConstructor
public class DataKey extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    /** Base64 of nonce || ciphertext || tag — the DEK encrypted with the master key. */
    @Column(name = "wrapped_key", nullable = false)
    private String wrappedKey;
}