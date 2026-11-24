package com.example.template.secret.repository;

import com.example.template.secret.model.Secret;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretRepository extends JpaRepository<Secret, UUID> {

    List<Secret> findAllByVaultIdOrderByNameAsc(UUID vaultId);

    Optional<Secret> findByIdAndVaultId(UUID id, UUID vaultId);

    long countByVaultIdAndFolderId(UUID vaultId, UUID folderId);

    void deleteAllByVaultId(UUID vaultId);
}