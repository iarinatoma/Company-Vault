package com.example.template.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Same password rule as registration (12–128 characters). */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {}