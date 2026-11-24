package com.example.template.access.api;

import com.example.template.access.dto.AccessLevelResponse;
import com.example.template.access.service.AccessControl;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v{version}/access")
public class AccessController {

    private final AccessControl accessControl;

    public AccessController(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    /** The caller's own level on one resource — the frontend shows or hides buttons with it. */
    @GetMapping(path = "/{resourceType}/{resourceId}/me", version = "1")
    @PreAuthorize("isAuthenticated()")
    public AccessLevelResponse myLevel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable ResourceType resourceType,
            @PathVariable UUID resourceId) {
        return accessControl.describe(principal, new ResourceRef(resourceType, resourceId));
    }
}