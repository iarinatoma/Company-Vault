package com.example.template.auth.repository;

import com.example.template.auth.model.ConsumedTwoFactorToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedTwoFactorTokenRepository extends JpaRepository<ConsumedTwoFactorToken, UUID> {

    boolean existsByJti(String jti);
}
