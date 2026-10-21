package com.example.template.auth.repository;

import com.example.template.auth.model.RecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findAllByUserIdAndUsedAtIsNull(UUID userId);

    /** Bulk JPQL on purpose: used when a user is deleted, before the users row goes. */
    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    int deleteAllByUserId(UUID userId);
}
