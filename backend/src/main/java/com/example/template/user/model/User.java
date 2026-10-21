package com.example.template.user.model;

import com.example.template.shared.model.BaseEntity;
import com.example.template.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    /** AES-GCM-encrypted TOTP secret — never stored plaintext, null until 2FA is enrolled. */
    @Column(name = "totp_secret", length = 512)
    private @Nullable String totpSecret;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    /** Failed passwords and failed 2FA verifications count into this same counter. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private @Nullable Instant lockedUntil;
}
