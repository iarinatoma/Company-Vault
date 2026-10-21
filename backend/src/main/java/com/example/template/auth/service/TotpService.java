package com.example.template.auth.service;

import com.example.template.auth.repository.RecoveryCodeRepository;
import com.example.template.shared.crypto.AeadEncryptor;
import com.example.template.user.model.User;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies second factors: TOTP against the AEAD-encrypted secret, with hashed single-use
 * recovery codes as the fallback.
 */
@Service
public class TotpService {

    private final AeadEncryptor aeadEncryptor;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final CodeVerifier codeVerifier;

    public TotpService(
            AeadEncryptor aeadEncryptor,
            RecoveryCodeRepository recoveryCodeRepository,
            PasswordEncoder passwordEncoder) {
        this.aeadEncryptor = aeadEncryptor;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    }

    /**
     * Tries the code as a TOTP first, then as a recovery code (consumed on success).
     * Participates in the caller's transaction so a consumed recovery code only commits with
     * the successful verification.
     */
    public boolean verify(User user, String code) {
        String encryptedSecret = user.getTotpSecret();
        if (encryptedSecret != null && codeVerifier.isValidCode(aeadEncryptor.decrypt(encryptedSecret), code)) {
            return true;
        }
        for (var recoveryCode : recoveryCodeRepository.findAllByUserIdAndUsedAtIsNull(user.requireId())) {
            if (passwordEncoder.matches(code, recoveryCode.getCodeHash())) {
                recoveryCode.setUsedAt(Instant.now());
                return true;
            }
        }
        return false;
    }
}
