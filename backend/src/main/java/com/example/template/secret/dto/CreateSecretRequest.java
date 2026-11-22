package com.example.template.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new secret. The value travels only in the request body, never in a URL, and is encrypted before storage. */
public record CreateSecretRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 10000) String value) {}