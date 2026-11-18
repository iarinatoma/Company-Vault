package com.example.template.keys.api;

import com.example.template.keys.dto.KeyRotationResponse;
import com.example.template.keys.service.MasterKeyRotation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Key management — administrators only. */
@RestController
@RequestMapping("/api/v{version}/keys")
public class KeyRotationController {

    private final MasterKeyRotation masterKeyRotation;

    public KeyRotationController(MasterKeyRotation masterKeyRotation) {
        this.masterKeyRotation = masterKeyRotation;
    }

    @Operation(
            summary = "Re-encrypt all data keys and TOTP secrets with the current master key",
            description = "Step 3 of a master key rotation (see backend/README.md): run it while both "
                    + "ENCRYPTION_KEY (new) and ENCRYPTION_KEY_PREVIOUS (old) are set. All or nothing.")
    @PostMapping(path = "/rotate-master", version = "1")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public KeyRotationResponse rotateMasterKey() {
        return masterKeyRotation.rotate();
    }
}