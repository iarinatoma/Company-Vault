package com.example.template.secret.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "secrets")
@Getter
@Setter
@NoArgsConstructor
public class Secret extends BaseEntity {

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "folder_id")
    private @Nullable UUID folderId;

    @Column(nullable = false, length = 200)
    private String name;

    /** What kind of secret this is (spec 4.3); plain metadata, so the list can show it without decrypting. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecretType type = SecretType.API_KEY;

    @Column(length = 1000)
    private @Nullable String description;

    /** Normalized comma-separated lowercase tags (e.g. "prod,aws,db") for plaintext metadata search. */
    @Column(name = "tags")
    private @Nullable String tags;

    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;

    /** Base64 of nonce || ciphertext || tag — never the plain value, never logged. */
    @Column(name = "encrypted_value", nullable = false)
    private String encryptedValue;

    /**
     * Type-specific fields (username and URL of a LOGIN, custom key-value fields) as JSON, encrypted
     * with the vault's data key exactly like the value; null when the secret has none.
     */
    @Column(name = "encrypted_fields")
    private @Nullable String encryptedFields;

    /** The owner (spec 4.3 metadata). */
    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "updated_by")
    private @Nullable UUID updatedBy;
}