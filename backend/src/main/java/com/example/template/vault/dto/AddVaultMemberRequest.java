package com.example.template.vault.dto;

import com.example.template.shared.security.AccessLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Adds a user to a vault by e-mail — project owners cannot list users, but they know their colleagues' e-mails. */
public record AddVaultMemberRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull AccessLevel accessLevel) {}