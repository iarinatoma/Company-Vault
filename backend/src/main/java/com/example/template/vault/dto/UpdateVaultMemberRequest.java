package com.example.template.vault.dto;

import com.example.template.shared.security.AccessLevel;
import jakarta.validation.constraints.NotNull;

/** Changes a member's level on a vault; NO_ACCESS removes them. */
public record UpdateVaultMemberRequest(@NotNull AccessLevel accessLevel) {}