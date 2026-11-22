package com.example.template.vault.api;

import com.example.template.shared.security.AuthPrincipal;
import com.example.template.vault.dto.AddVaultMemberRequest;
import com.example.template.vault.dto.UpdateVaultMemberRequest;
import com.example.template.vault.dto.VaultMemberResponse;
import com.example.template.vault.service.VaultMemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Members of a vault. Who may call it (MANAGE on the vault, or an administrator) is checked in the service. */
@RestController
@RequestMapping("/api/v{version}/vaults/{vaultId}/members")
public class VaultMemberController {

    private final VaultMemberService vaultMemberService;

    public VaultMemberController(VaultMemberService vaultMemberService) {
        this.vaultMemberService = vaultMemberService;
    }

    @Operation(
            summary = "List who has access to a vault",
            description = "Members with MANAGE on the vault, or administrators (access recovery).")
    @GetMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public List<VaultMemberResponse> list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId) {
        return vaultMemberService.list(principal, vaultId);
    }

    @Operation(
            summary = "Add a member by e-mail",
            description = "Gives the user the level on the vault; if they are already a member, changes it. "
                    + "Auditors cannot be given access.")
    @PostMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public VaultMemberResponse add(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody AddVaultMemberRequest request) {
        return vaultMemberService.add(principal, vaultId, request);
    }

    @Operation(
            summary = "Change a member's level",
            description = "NO_ACCESS removes the member. The last member with MANAGE cannot be demoted (409 LAST_MANAGER).")
    @PutMapping(path = "/{userId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    public VaultMemberResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateVaultMemberRequest request) {
        return vaultMemberService.update(principal, vaultId, userId, request);
    }

    @Operation(
            summary = "Remove a member",
            description = "Takes effect immediately. The last member with MANAGE cannot be removed (409 LAST_MANAGER).")
    @DeleteMapping(path = "/{userId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId, @PathVariable UUID userId) {
        vaultMemberService.remove(principal, vaultId, userId);
    }
}