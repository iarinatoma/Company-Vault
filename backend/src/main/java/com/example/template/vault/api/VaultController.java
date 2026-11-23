package com.example.template.vault.api;

import com.example.template.shared.security.AuthPrincipal;
import com.example.template.vault.dto.CreateVaultRequest;
import com.example.template.vault.dto.VaultResponse;
import com.example.template.vault.service.VaultService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import com.example.template.vault.dto.CreateProjectVaultRequest;

@RestController
@RequestMapping("/api/v{version}/vaults")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    // Every logged-in user; the service decides which vaults they see
    @Operation(
        summary = "List the vaults the caller can see",
        description = "Administrators get every active vault (metadata only); everyone else the vaults they hold at least READ on.")
    @GetMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public List<VaultResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return vaultService.list(principal);
    }

    // Admins always; everyone else needs at least READ on the vault (checked in the service)
    
    @Operation(
            summary = "Get one vault's metadata and the caller's access level",
            description = "Administrators always; everyone else needs at least READ, otherwise 403.")    
    @GetMapping(path = "/{id}", version = "1")
    @PreAuthorize("isAuthenticated()")
    public VaultResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return vaultService.get(principal, id);
    }

    // Only administrators create general vaults
    @Operation(
            summary = "Create a general vault",
            description = "Administrators only. The chosen owner gets MANAGE; the creating admin gets no access to the vault's secrets.")
    @PostMapping(version = "1")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public VaultResponse create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CreateVaultRequest request) {
        return vaultService.createGeneral(principal, request);
    }

    @Operation(
            summary = "Create a project vault",
            description = "Administrators and project owners. A project owner becomes its manager; an administrator "
                    + "must choose the owner and gets no access to its secrets unless they choose themselves.")
    @PostMapping(path = "/projects", version = "1")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'PROJECT_OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VaultResponse createProject(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CreateProjectVaultRequest request) {
        return vaultService.createProject(principal, request);
    }

}