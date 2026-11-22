package com.example.template.access.dto;

import com.example.template.shared.security.AccessLevel;
import java.util.UUID;

/** One user's stored level on one resource — a row of "who has access to this?". */
public record ResourceMember(UUID userId, AccessLevel level) {}