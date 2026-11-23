package com.example.template.folder.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FolderResponse(
        UUID id,
        UUID vaultId,
        String name,
        long secretCount,
        @Nullable String createdByEmail,
        Instant createdAt,
        Instant updatedAt) {}