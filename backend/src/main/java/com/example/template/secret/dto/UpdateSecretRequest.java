package com.example.template.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record UpdateSecretRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) @Nullable String value,
        @Size(max = 1000) @Nullable String description,
        @Size(max = 20) @Nullable List<@NotBlank @Size(max = 50) String> tags,
        @Nullable Instant expiresAt) {

    public UpdateSecretRequest(String name, @Nullable String value) {
        this(name, value, null, null, null);
    }
}