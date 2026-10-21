package com.example.template.user.dto;

import com.example.template.shared.security.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, boolean twoFactorEnabled, Instant createdAt) {}
