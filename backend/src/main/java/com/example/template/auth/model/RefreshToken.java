package com.example.template.auth.model;

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

/**
 * Opaque refresh tokens are stored only as hashes. Each token belongs to a session family;
 * rotation invalidates the predecessor, and presenting a rotated token revokes the whole
 * family. The cross-feature reference to the user is a plain UUID — never a JPA
 * relationship across a feature boundary; the foreign key lives in Liquibase.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hex of the opaque token — the raw value is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;
}
