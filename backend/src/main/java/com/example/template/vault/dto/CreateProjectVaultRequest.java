package com.example.template.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new project vault. A project owner creating it becomes its manager themselves, so they leave
 * {@code ownerId} empty; an administrator must name the owner (and gets no access unless it is them).
 */
public record CreateProjectVaultRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) @Nullable String description,
        @Nullable UUID ownerId) {}