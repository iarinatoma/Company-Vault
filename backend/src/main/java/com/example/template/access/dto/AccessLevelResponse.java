package com.example.template.access.dto;

import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.ResourceType;
import java.util.UUID;

/** The caller's effective access level on one resource. */
public record AccessLevelResponse(ResourceType resourceType, UUID resourceId, AccessLevel level) {}