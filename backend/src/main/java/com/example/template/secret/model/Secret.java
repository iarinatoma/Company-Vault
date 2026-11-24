package com.example.template.secret.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false, length = 200)
    private String name;

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

    /** The owner (spec 4.3 metadata). */
    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "updated_by")
    private @Nullable UUID updatedBy;
}