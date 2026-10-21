package com.example.template.auth.repository;

import com.example.template.auth.model.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);

    /** Bulk JPQL on purpose: a derived deleteBy would load and delete row by row. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(Instant cutoff);

    /** Bulk JPQL on purpose: used when a user is deleted, before the users row goes. */
    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId")
    int deleteAllByUserId(UUID userId);
}
