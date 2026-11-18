package com.example.template.user.repository;

import com.example.template.user.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Users with 2FA enrolled: their TOTP secret is encrypted with the master key. */
    List<User> findAllByTotpSecretIsNotNull();
}