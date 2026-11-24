package com.example.template.secret.repository;

import com.example.template.secret.model.Secret;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecretRepository extends JpaRepository<Secret, UUID> {

    List<Secret> findAllByVaultIdOrderByNameAsc(UUID vaultId);

    Optional<Secret> findByIdAndVaultId(UUID id, UUID vaultId);

    /** Deletes all secret metadata and ciphertext rows belonging to a vault being deleted. */
    void deleteAllByVaultId(UUID vaultId);
    long countByVaultIdAndFolderId(UUID vaultId, UUID folderId);

    @Modifying
    @Query("update Secret s set s.folderId = null where s.vaultId = :vaultId and s.folderId = :folderId")
    void clearFolderId(@Param("vaultId") UUID vaultId, @Param("folderId") UUID folderId);
}