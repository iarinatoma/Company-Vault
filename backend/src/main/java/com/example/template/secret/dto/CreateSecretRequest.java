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
 * A new secret. {@code value} is the main value of every type (password, key/token, note text,
 * main value of a custom secret); {@code type} defaults to API_KEY; {@code fields} carries the
 * type-specific fields and is optional.
 */
public record CreateSecretRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 10000) String value,
        @Nullable UUID folderId,
        @Size(max = 1000) @Nullable String description,
        @Size(max = 20) @Nullable List<@NotBlank @Size(max = 50) String> tags,
        @Nullable Instant expiresAt,
        @Nullable SecretType type,
        @Valid @Nullable SecretFields fields) {

    public CreateSecretRequest(String name, String value) {
        this(name, value, null, null, null, null, null, null);
    }

    /** Without type and fields: a secret with just a value, as before secret types existed. */
    public CreateSecretRequest(
            String name,
            String value,
            @Nullable UUID folderId,
            @Nullable String description,
            @Nullable List<String> tags,
            @Nullable Instant expiresAt) {
        this(name, value, folderId, description, tags, expiresAt, null, null);
    }
}