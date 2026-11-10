package com.example.template.access.model;

import com.example.template.shared.model.BaseEntity;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One user's access level on one resource. Generic on purpose: the resource is a type plus a
 * plain UUID, so vaults, projects, folders and secrets all use the same table. At most one row
 * per user and resource (unique constraint in Liquibase); no row means NO_ACCESS.
 */
@Entity
@Table(name = "access_grants")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrant extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 16)
    private AccessLevel accessLevel;
}