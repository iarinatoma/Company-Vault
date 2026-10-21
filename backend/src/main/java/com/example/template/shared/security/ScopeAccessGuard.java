package com.example.template.shared.security;

import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The second half of the double enforcement: {@code @PreAuthorize} on the controller declares
 * the role rule; every service method touching scoped data must take its decision from here.
 * A scoped service method that never consults this guard is an isolation hole even when the
 * annotation looks right. The switches are exhaustive on purpose — adding a role must fail
 * compilation here.
 */
@Component
public class ScopeAccessGuard {

    /**
     * Requires that the caller may reach data owned by {@code ownerId}.
     */
    public void requireScopeAccess(AuthPrincipal principal, UUID ownerId) {
        boolean allowed =
                switch (principal.role()) {
                    case ADMIN, MANAGER -> true;
                    case USER -> principal.userId().equals(ownerId);
                };
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Requires unrestricted access across all scopes (e.g. listing every user).
     */
    public void requireAllScopes(AuthPrincipal principal) {
        boolean allowed =
                switch (principal.role()) {
                    case ADMIN, MANAGER -> true;
                    case USER -> false;
                };
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
