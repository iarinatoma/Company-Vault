package com.example.template.folder.model;

import com.example.template.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder extends BaseEntity {

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "updated_by")
    private @Nullable UUID updatedBy;
}