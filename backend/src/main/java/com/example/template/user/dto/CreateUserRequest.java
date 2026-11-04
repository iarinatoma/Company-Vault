package com.example.template.user.dto;

import com.example.template.shared.security.Role;
import com.example.template.shared.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @StrongPassword String password,
        @NotNull Role role) {}