package com.example.template.auth.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The 2FA challenge token's {@code jti} is consumed on successful verification so it cannot
 * be replayed. Rows are dead weight once {@code expiresAt} passes — a periodic cleanup job
 * may delete them.
 */
@Entity
@Table(name = "consumed_two_factor_tokens")
@Getter
@Setter
@NoArgsConstructor
public class ConsumedTwoFactorToken extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
