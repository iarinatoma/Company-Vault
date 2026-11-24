package com.example.template.vault.dto;

import com.example.template.shared.security.AccessLevel;
import java.util.UUID;

/** A member of a vault: who they are and what they may do there. */
public record VaultMemberResponse(UUID userId, String email, AccessLevel accessLevel) {}