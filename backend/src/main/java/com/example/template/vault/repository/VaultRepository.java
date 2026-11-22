package com.example.template.vault.repository;

import com.example.template.vault.model.Vault;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultRepository extends JpaRepository<Vault, UUID> {

    /** Every active vault — the admin overview (metadata only). */
    List<Vault> findAllByArchivedFalseOrderByNameAsc();

    /** The active vaults among the given ids — the vaults a user has a grant on. */
    List<Vault> findAllByIdInAndArchivedFalseOrderByNameAsc(Collection<UUID> ids);
}