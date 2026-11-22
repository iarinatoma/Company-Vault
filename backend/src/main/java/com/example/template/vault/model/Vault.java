package com.example.template.vault.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * A collection of secrets. Who may use it lives in access_grants and its data encryption key in
 * data_keys, both keyed by (VAULT, id) — the vault row itself holds only non-sensitive metadata.
 */
@Entity
@Table(name = "vaults")
@Getter
@Setter
@NoArgsConstructor
public class Vault extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private @Nullable String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VaultType type;

    @Column(nullable = false)
    private boolean archived;

    /** The admin/user who created it — information only; ownership is a MANAGE grant. */
    @Column(name = "created_by")
    private @Nullable UUID createdBy;
}