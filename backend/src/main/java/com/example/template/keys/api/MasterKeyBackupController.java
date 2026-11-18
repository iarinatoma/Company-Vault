package com.example.template.keys.api;

import com.example.template.keys.service.MasterKeyBackupService;
import com.example.template.shared.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v{version}/admin/master-key")
public class MasterKeyBackupController {

    private final MasterKeyBackupService masterKeyBackupService;

    public MasterKeyBackupController(MasterKeyBackupService masterKeyBackupService) {
        this.masterKeyBackupService = masterKeyBackupService;
    }

    @GetMapping(path = "/backup", version = "1", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<byte[]> downloadBackup(@AuthenticationPrincipal AuthPrincipal principal) {
        byte[] content =
                (masterKeyBackupService.backupValue(principal.userId()) + "\n").getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("company-vault-master-key.txt", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(content);
    }
}
