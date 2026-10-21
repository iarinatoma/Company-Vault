package com.example.template.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(
        @NotBlank String challengeToken,
        @NotBlank @Size(max = 64) String code) {}
