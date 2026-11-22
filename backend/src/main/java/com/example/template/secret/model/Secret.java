package com.example.template.secret.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * A secret in a vault. Only the name is plain text (listable and searchable); the value is stored
 * encrypted with the vault's data key and is decrypted only on an explicit reveal. The vault is a
 * plain UUID column — the foreign key lives in Liquibase, entities never cross feature boundaries.
 */
@Entity
@Table(name = "secrets")
@Getter
@Setter
@NoArgsConstructor
public class Secret extends BaseEntity {

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Base64 of nonce || ciphertext || tag — never the plain value, never logged. */
    @Column(name = "encrypted_value", nullable = false)
    private String encryptedValue;

    /** The owner (spec 4.3 metadata). */
    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "updated_by")
    private @Nullable UUID updatedBy;
}