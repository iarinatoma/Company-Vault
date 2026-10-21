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
 * Recovery codes are hashed like passwords and are single-use.
 */
@Entity
@Table(name = "recovery_codes")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryCode extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;
}
