package com.example.template.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One custom key-value field of a secret ("eigene Felder"); stored only encrypted. */
public record CustomField(@NotBlank @Size(max = 100) String name, @NotNull @Size(max = 5000) String value) {}