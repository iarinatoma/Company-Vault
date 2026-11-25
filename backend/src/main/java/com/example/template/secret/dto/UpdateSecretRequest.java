package com.example.template.secret.dto;

import com.example.template.secret.model.SecretType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An edit. An empty {@code value} keeps the current one; a null {@code type} keeps the current type;
 * null {@code fields} keep the current type-specific fields, while non-null fields replace them.
 */
public record UpdateSecretRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) @Nullable String value,
        @Nullable UUID folderId,
        @Size(max = 1000) @Nullable String description,
        @Size(max = 20) @Nullable List<@NotBlank @Size(max = 50) String> tags,
        @Nullable Instant expiresAt,
        @Nullable SecretType type,
        @Valid @Nullable SecretFields fields) {

    public UpdateSecretRequest(String name, @Nullable String value) {
        this(name, value, null, null, null, null, null, null);
    }

    /** Without type and fields: changes metadata and value only, as before secret types existed. */
    public UpdateSecretRequest(
            String name,
            @Nullable String value,
            @Nullable UUID folderId,
            @Nullable String description,
            @Nullable List<String> tags,
            @Nullable Instant expiresAt) {
        this(name, value, folderId, description, tags, expiresAt, null, null);
    }
}