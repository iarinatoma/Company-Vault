package com.example.template.folder.api;

import com.example.template.folder.dto.CreateFolderRequest;
import com.example.template.folder.dto.FolderResponse;
import com.example.template.folder.dto.UpdateFolderRequest;
import com.example.template.folder.service.FolderService;
import com.example.template.shared.security.AuthPrincipal;
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

@RestController
@RequestMapping("/api/v{version}/vaults/{vaultId}/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @Operation(summary = "List folders in a vault", description = "Needs READ on the vault.")
    @GetMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public List<FolderResponse> list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId) {
        return folderService.list(principal, vaultId);
    }

    @Operation(summary = "Create a folder in a vault", description = "Needs WRITE on the vault.")
    @PostMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody CreateFolderRequest request) {
        return folderService.create(principal, vaultId, request);
    }

    @Operation(summary = "Rename a folder", description = "Needs WRITE on the vault.")
    @PutMapping(path = "/{folderId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    public FolderResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID folderId,
            @Valid @RequestBody UpdateFolderRequest request) {
        return folderService.update(principal, vaultId, folderId, request);
    }

    @Operation(
            summary = "Delete a folder",
            description = "Needs MANAGE on the vault. Secrets inside are moved to the vault root (folderId = null).")
    @DeleteMapping(path = "/{folderId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId, @PathVariable UUID folderId) {
        folderService.delete(principal, vaultId, folderId);
    }
}