package com.example.template.keys.repository;

import com.example.template.keys.model.DataKey;
import com.example.template.shared.security.ResourceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataKeyRepository extends JpaRepository<DataKey, UUID> {

    /** The wrapped DEK of one vault/project, if it has one. */
    Optional<DataKey> findByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);

    boolean existsByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);

    /** Deleting the DEK makes every secret encrypted with it unreadable for good. */
    void deleteByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);
}