package com.example.template.user.service;

import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ScopeAccessGuard;
import com.example.template.user.dto.CreateUserRequest;
import com.example.template.user.dto.UpdateUserRequest;
import com.example.template.user.dto.UserResponse;
import com.example.template.user.event.UserCreatedEvent;
import com.example.template.user.event.UserDeletedEvent;
import com.example.template.user.mapper.UserMapper;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes and returns DTOs — entities never leave the service; the mapper is a service
 * collaborator, never a controller one.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ScopeAccessGuard scopeAccessGuard;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            ScopeAccessGuard scopeAccessGuard,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.scopeAccessGuard = scopeAccessGuard;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(AuthPrincipal principal, UUID id) {
        scopeAccessGuard.requireScopeAccess(principal, id);
        return userRepository
                .findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserResponse getOwn(AuthPrincipal principal) {
        return userRepository
                .findById(principal.userId())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PagedModel<UserResponse> list(AuthPrincipal principal, Pageable pageable) {
        scopeAccessGuard.requireAllScopes(principal);
        return new PagedModel<>(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }
        var user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        var saved = userRepository.save(user);
        log.info("User {} created with role {}", saved.requireId(), saved.getRole());
        eventPublisher.publishEvent(new UserCreatedEvent(saved.requireId(), saved.getEmail()));
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(AuthPrincipal principal, UUID id, UpdateUserRequest request) {
        var user = userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (principal.userId().equals(id) && request.role() != user.getRole()) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }
        user.setEmail(request.email());
        user.setRole(request.role());
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        log.info("User {} updated by {}", id, principal.userId());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(AuthPrincipal principal, UUID id) {
        if (principal.userId().equals(id)) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        var user = userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        eventPublisher.publishEvent(new UserDeletedEvent(id, user.getEmail()));
        userRepository.delete(user);
        log.info("User {} deleted by {}", id, principal.userId());
    }
}
