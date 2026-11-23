package com.example.template.secret.repository;

import com.example.template.secret.model.Secret;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretRepository extends JpaRepository<Secret, UUID> {

    /** All secrets of one vault, by name — metadata only is ever sent out of the service. */
    List<Secret> findAllByVaultIdOrderByNameAsc(UUID vaultId);

    /** A secret only if it belongs to that vault — access to vault A never reaches a secret of vault B. */
    Optional<Secret> findByIdAndVaultId(UUID id, UUID vaultId);

    /** Deletes all secret metadata and ciphertext rows belonging to a vault being deleted. */
    void deleteAllByVaultId(UUID vaultId);
}