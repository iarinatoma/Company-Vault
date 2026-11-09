package com.example.template.access.repository;

import com.example.template.access.model.AccessGrant;
import com.example.template.shared.security.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    /** The grant of one user on one resource, if any — absent means NO_ACCESS. */
    Optional<AccessGrant> findByUserIdAndResourceTypeAndResourceId(
            UUID userId, ResourceType resourceType, UUID resourceId);

    /** Everyone with a grant on one resource — the base of the "who has access?" view. */
    List<AccessGrant> findAllByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);

    /** Bulk JPQL on purpose: used when a user is deleted, before the users row goes. */
    @Modifying
    @Query("delete from AccessGrant g where g.userId = :userId")
    int deleteAllByUserId(UUID userId);
}