package com.example.template.generator.api;

import com.example.template.generator.dto.GeneratedPasswordResponse;
import com.example.template.generator.service.PasswordGenerator;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Password generator for any signed-in user (spec 4.3). */
@RestController
@RequestMapping("/api/v{version}/password-generator")
public class PasswordGeneratorController {

    private final PasswordGenerator passwordGenerator;

    public PasswordGeneratorController(PasswordGenerator passwordGenerator) {
        this.passwordGenerator = passwordGenerator;
    }

    @Operation(
            summary = "Generate a random password",
            description = "Any signed-in user. Length 8–128 (default 20); at least one character set (all on by "
                    + "default). Every chosen set appears at least once. The password is only in the response "
                    + "body — never stored, logged or put in a URL.")
    @GetMapping(version = "1")
    @PreAuthorize("isAuthenticated()")
    public GeneratedPasswordResponse generate(
            @RequestParam(defaultValue = "20") int length,
            @RequestParam(defaultValue = "true") boolean lowercase,
            @RequestParam(defaultValue = "true") boolean uppercase,
            @RequestParam(defaultValue = "true") boolean digits,
            @RequestParam(defaultValue = "true") boolean symbols) {
        return passwordGenerator.generate(length, lowercase, uppercase, digits, symbols);
    }
}