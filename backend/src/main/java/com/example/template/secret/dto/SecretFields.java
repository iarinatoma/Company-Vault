package com.example.template.secret.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The type-specific fields of a secret: username and URL of a LOGIN, and custom key-value fields
 * of any type. Stored as JSON encrypted with the vault's data key — never listed, never searched,
 * returned only by the explicit reveal.
 */
public record SecretFields(
        @Size(max = 200) @Nullable String username,
        @Size(max = 2000) @Nullable String url,
        @Size(max = 20) List<@Valid CustomField> customFields) {

    public static final SecretFields EMPTY = new SecretFields(null, null, List.of());

    public SecretFields {
        customFields = customFields == null
                ? List.of()
                : customFields.stream().filter(Objects::nonNull).toList();
    }
}