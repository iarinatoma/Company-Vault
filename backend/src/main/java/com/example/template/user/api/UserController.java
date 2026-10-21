package com.example.template.user.api;

import com.example.template.shared.security.AuthPrincipal;
import com.example.template.user.dto.CreateUserRequest;
import com.example.template.user.dto.UpdateUserRequest;
import com.example.template.user.dto.UserResponse;
import com.example.template.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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

import java.util.UUID;

/**
 * Pure delegation: the controller sends DTOs to the service and returns the DTOs it gets
 * back. It never maps and never sees an entity — mapping lives in the service.
 */
@RestController
@RequestMapping("/api/v{version}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(path = "/me", version = "1")
    @PreAuthorize("isAuthenticated()")
    public UserResponse getOwnProfile(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getOwn(principal);
    }

    @GetMapping(path = "/{id}", version = "1")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public UserResponse getById(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return userService.getById(principal, id);
    }

    @GetMapping(version = "1")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PagedModel<UserResponse> list(@AuthenticationPrincipal AuthPrincipal principal, Pageable pageable) {
        return userService.list(principal, pageable);
    }

    @PostMapping(version = "1")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping(path = "/{id}", version = "1")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(principal, id, request);
    }

    @DeleteMapping(path = "/{id}", version = "1")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        userService.delete(principal, id);
    }
}
