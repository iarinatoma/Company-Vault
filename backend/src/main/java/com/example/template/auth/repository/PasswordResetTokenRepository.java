package com.example.template.auth.repository;

import com.example.template.auth.model.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Still-open tokens of a user — a new request or a successful reset invalidates them. */
    List<PasswordResetToken> findAllByUserIdAndUsedAtIsNull(UUID userId);

    /** Bulk JPQL on purpose: used when a user is deleted, before the users row goes. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.userId = :userId")
    int deleteAllByUserId(UUID userId);
}