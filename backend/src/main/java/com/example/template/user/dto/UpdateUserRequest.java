package com.example.template.user.dto;

import com.example.template.shared.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Full replacement of the editable fields; an absent password keeps the current one. */
public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull Role role,
        @Size(min = 12, max = 128) @Nullable String password) {}
