package com.example.template.secret.api;

import com.example.template.secret.dto.CreateSecretRequest;
import com.example.template.secret.dto.MoveSecretRequest;
import com.example.template.secret.dto.SecretResponse;
import com.example.template.secret.dto.SecretValueResponse;
import com.example.template.secret.dto.UpdateSecretRequest;
import com.example.template.secret.service.SecretService;
import com.example.template.shared.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Secrets of a vault. The access level on the vault is checked in the service. */
@RestController
@RequestMapping("/api/v{version}/vaults/{vaultId}/secrets")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @Operation(
            summary = "List or filter the secrets of a vault",
            description = "Needs READ on the vault. Searches only plain-text name and tags; values stay masked.")
    @GetMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public List<SecretResponse> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable String tag,
            @RequestParam(required = false) @Nullable UUID folderId,
            @RequestParam(defaultValue = "false") boolean rootOnly) {
        return secretService.list(principal, vaultId, q, tag, folderId, rootOnly);
    }

    @Operation(
            summary = "Reveal one secret's value",
            description = "Needs READ on the vault. Decrypts the value on explicit request; the value is in the "
                    + "response body only, never in a URL or log.")
    @GetMapping(path = "/{secretId}/value", version = "1")
    @PreAuthorize("isAuthenticated()")
    public SecretValueResponse reveal(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId, @PathVariable UUID secretId) {
        return secretService.reveal(principal, vaultId, secretId);
    }

    @Operation(
            summary = "Create a secret",
            description = "Needs WRITE on the vault, which must not be archived. The value is encrypted with the "
                    + "vault's data key before it is stored.")
    @PostMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public SecretResponse create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody CreateSecretRequest request) {
        return secretService.create(principal, vaultId, request);
    }

    @Operation(
            summary = "Update a secret's metadata and optionally replace its value",
            description = "Needs WRITE on the vault, which must not be archived. An empty value keeps the current one.")
    @PutMapping(path = "/{secretId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    public SecretResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID secretId,
            @Valid @RequestBody UpdateSecretRequest request) {
        return secretService.update(principal, vaultId, secretId, request);
    }

    @Operation(
            summary = "Move a secret to another folder or vault",
            description = "Same-vault moves update folderId without re-encryption; cross-vault moves require MANAGE "
                    + "on the source vault and WRITE on the target vault and re-encrypt with the target vault's DEK.")
    @PostMapping(path = "/{secretId}/move", version = "1")
    @PreAuthorize("isAuthenticated()")
    public SecretResponse move(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID secretId,
            @Valid @RequestBody MoveSecretRequest request) {
        return secretService.move(principal, vaultId, secretId, request);
    }

    @Operation(summary = "Delete a secret", description = "Needs MANAGE on the vault, which must not be archived.")
    @DeleteMapping(path = "/{secretId}", version = "1")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID vaultId, @PathVariable UUID secretId) {
        secretService.delete(principal, vaultId, secretId);
    }
}