package com.example.template.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Renames a secret and, if {@code value} is given, replaces its value; an empty value keeps the current one. */
public record UpdateSecretRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) @Nullable String value) {}