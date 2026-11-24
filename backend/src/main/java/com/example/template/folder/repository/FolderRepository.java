package com.example.template.folder.repository;

import com.example.template.folder.model.Folder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findAllByVaultIdOrderByNameAsc(UUID vaultId);

    Optional<Folder> findByIdAndVaultId(UUID id, UUID vaultId);

    boolean existsByIdAndVaultId(UUID id, UUID vaultId);

    boolean existsByVaultIdAndNameIgnoreCase(UUID vaultId, String name);

    boolean existsByVaultIdAndNameIgnoreCaseAndIdNot(UUID vaultId, String name, UUID id);
}