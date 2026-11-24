package com.example.template.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** New name and description of a vault (rename). */
public record UpdateVaultRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) @Nullable String description) {}