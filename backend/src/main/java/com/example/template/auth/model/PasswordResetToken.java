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
 * Single-use, short-lived token that lets a user set a new password via an e-mail link.
 * Only the SHA-256 hash is stored; the raw token exists only in the e-mail. The reference
 * to the user is a plain UUID — the foreign key lives in Liquibase.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hex of the opaque token — the raw value is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is redeemed or superseded by a newer request; a used token never works again. */
    @Column(name = "used_at")
    private @Nullable Instant usedAt;
}