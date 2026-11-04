package com.example.template.auth.api;

import com.example.template.auth.dto.ForgotPasswordRequest;
import com.example.template.auth.dto.ResetPasswordRequest;
import com.example.template.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous forgot-password flow. Both paths are already covered by AuthRateLimitFilter —
 * keep them exactly as they are.
 */
@RestController
@RequestMapping("/api/v{version}/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(
            summary = "Request a password reset link",
            description = "Always answers 202, whether or not the e-mail belongs to an account. "
                    + "If it does, a single-use link is sent by e-mail.")
    @ApiResponse(responseCode = "202", description = "Request accepted")
    @PostMapping(path = "/forgot-password", version = "1")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
    }

    @Operation(summary = "Set a new password with the token from the reset link")
    @ApiResponse(responseCode = "204", description = "Password changed; all sessions signed out")
    @ApiResponse(responseCode = "401", description = "Token unknown, expired or already used")
    @PostMapping(path = "/reset-password", version = "1")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }
}