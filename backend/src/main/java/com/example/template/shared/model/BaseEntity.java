package com.example.template.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Every entity extends this. It owns the id (time-ordered UUIDv7 — index-friendly under
 * Postgres), the audit timestamps (set by the entity itself in JPA callbacks — not by JPA
 * auditing, Liquibase defaults, or triggers), optimistic locking, and identity-based
 * equals/hashCode. Skipping it — or hand-setting timestamps, or touching {@code version}
 * in a service — needs a reason in the PR, not a habit.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private @Nullable UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    /**
     * Every update is guarded by {@code WHERE version = ?}; a concurrent modification surfaces
     * as an {@code OptimisticLockException} handled by {@code GlobalExceptionHandler} — never
     * catch it in a service.
     */
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    /**
     * The id, guaranteed present. Valid only on persisted entities — repositories never return
     * one without an id; calling this on a new, unsaved entity is a programming error. Prefer
     * this over {@code getId()} when passing the id onward under {@code @NullMarked}.
     */
    public UUID requireId() {
        return Objects.requireNonNull(id, "entity has no id yet — it is not persisted");
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BaseEntity that = (BaseEntity) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
