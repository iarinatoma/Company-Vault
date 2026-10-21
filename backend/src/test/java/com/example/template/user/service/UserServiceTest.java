package com.example.template.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.Role;
import com.example.template.shared.security.ScopeAccessGuard;
import com.example.template.user.dto.CreateUserRequest;
import com.example.template.user.dto.UpdateUserRequest;
import com.example.template.user.dto.UserResponse;
import com.example.template.user.event.UserCreatedEvent;
import com.example.template.user.event.UserDeletedEvent;
import com.example.template.user.mapper.UserMapper;
import com.example.template.user.model.User;
import com.example.template.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ScopeAccessGuard scopeAccessGuard;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(CALLER_ID, "caller@example.com", Role.USER);

    private static User user() {
        var user = new User();
        user.setEmail("someone@example.com");
        user.setPasswordHash("{bcrypt}hash");
        user.setRole(Role.USER);
        return user;
    }

    private static UserResponse response(String email) {
        return new UserResponse(UUID.randomUUID(), email, Role.USER, false, Instant.now());
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("returns the mapped DTO when the scope guard allows access")
        void getById_scopeAllowed_returnsResponse() {
            var target = user();
            var expected = response(target.getEmail());
            when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(target));
            when(userMapper.toResponse(target)).thenReturn(expected);

            var result = userService.getById(PRINCIPAL, CALLER_ID);

            assertThat(result).isSameAs(expected);
            verify(scopeAccessGuard).requireScopeAccess(PRINCIPAL, CALLER_ID);
        }

        @Test
        @DisplayName("propagates FORBIDDEN and never touches the repository when the guard denies")
        void getById_scopeDenied_throwsForbiddenWithoutRepositoryAccess() {
            var otherId = UUID.randomUUID();
            doThrow(new ApiException(ErrorCode.FORBIDDEN))
                    .when(scopeAccessGuard)
                    .requireScopeAccess(PRINCIPAL, otherId);

            assertThatThrownBy(() -> userService.getById(PRINCIPAL, otherId))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when the id does not exist")
        void getById_unknownId_throwsUserNotFound() {
            when(userRepository.findById(CALLER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(PRINCIPAL, CALLER_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("returns the mapped page envelope when the caller may reach all scopes")
        void list_allScopesAllowed_returnsPagedResponses() {
            Pageable pageable = PageRequest.of(0, 20);
            var target = user();
            Page<User> page = new PageImpl<>(List.of(target), pageable, 1);
            when(userRepository.findAll(pageable)).thenReturn(page);
            when(userMapper.toResponse(target)).thenReturn(response(target.getEmail()));

            var result = userService.list(PRINCIPAL, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(scopeAccessGuard).requireAllScopes(PRINCIPAL);
        }

        @Test
        @DisplayName("propagates FORBIDDEN and never queries when the guard denies")
        void list_scopeDenied_throwsForbiddenWithoutQuery() {
            Pageable pageable = PageRequest.of(0, 20);
            doThrow(new ApiException(ErrorCode.FORBIDDEN))
                    .when(scopeAccessGuard)
                    .requireAllScopes(PRINCIPAL);

            assertThatThrownBy(() -> userService.list(PRINCIPAL, pageable))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
            verify(userRepository, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        private final CreateUserRequest request =
                new CreateUserRequest("new@example.com", "long-password-123", Role.USER);

        @Test
        @DisplayName("encodes the password, saves, publishes UserCreatedEvent and returns the mapped DTO")
        void create_emailFree_savesAndPublishesEvent() {
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("long-password-123")).thenReturn("{bcrypt}encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                var saved = invocation.getArgument(0, User.class);
                // Simulate persistence: the returned entity has an id, like the real repository's.
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                return saved;
            });
            when(userMapper.toResponse(any(User.class))).thenReturn(response("new@example.com"));

            var created = userService.create(request);

            assertThat(created.email()).isEqualTo("new@example.com");
            var savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUser.capture());
            assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("{bcrypt}encoded");
            verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));
        }

        @Test
        @DisplayName("throws EMAIL_ALREADY_IN_USE and never saves when the email is taken")
        void create_emailTaken_throwsConflictWithoutSaving() {
            when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.create(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_IN_USE);
            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        private final UUID targetId = UUID.randomUUID();

        @Test
        @DisplayName("applies e-mail and role, keeps the password when none is given")
        void update_noPassword_keepsHash() {
            var target = user();
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.save(target)).thenReturn(target);
            when(userMapper.toResponse(target)).thenReturn(response("new@example.com"));

            userService.update(PRINCIPAL, targetId, new UpdateUserRequest("new@example.com", Role.MANAGER, null));

            assertThat(target.getEmail()).isEqualTo("new@example.com");
            assertThat(target.getRole()).isEqualTo(Role.MANAGER);
            assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}hash");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("re-encodes the password when one is given")
        void update_withPassword_encodesIt() {
            var target = user();
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(passwordEncoder.encode("another-long-password")).thenReturn("{bcrypt}new");
            when(userRepository.save(target)).thenReturn(target);
            when(userMapper.toResponse(target)).thenReturn(response(target.getEmail()));

            userService.update(
                    PRINCIPAL, targetId, new UpdateUserRequest(target.getEmail(), Role.USER, "another-long-password"));

            assertThat(target.getPasswordHash()).isEqualTo("{bcrypt}new");
        }

        @Test
        @DisplayName("throws EMAIL_ALREADY_IN_USE when the new e-mail is taken")
        void update_emailTaken_throwsConflict() {
            when(userRepository.findById(targetId)).thenReturn(Optional.of(user()));
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() ->
                            userService.update(PRINCIPAL, targetId, new UpdateUserRequest("taken@example.com", Role.USER, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_IN_USE);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses to change the caller's own role")
        void update_ownRole_throwsConflict() {
            var self = user();
            when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(self));

            assertThatThrownBy(() ->
                            userService.update(PRINCIPAL, CALLER_ID, new UpdateUserRequest(self.getEmail(), Role.ADMIN, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND for an unknown id")
        void update_unknownId_throwsNotFound() {
            when(userRepository.findById(targetId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                            userService.update(PRINCIPAL, targetId, new UpdateUserRequest("a@example.com", Role.USER, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("publishes UserDeletedEvent, then deletes the user")
        void delete_existingUser_publishesEventAndDeletes() {
            var targetId = UUID.randomUUID();
            var target = user();
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

            userService.delete(PRINCIPAL, targetId);

            var order = org.mockito.Mockito.inOrder(eventPublisher, userRepository);
            order.verify(eventPublisher).publishEvent(any(UserDeletedEvent.class));
            order.verify(userRepository).delete(target);
        }

        @Test
        @DisplayName("refuses to delete the caller's own account")
        void delete_self_throwsConflict() {
            assertThatThrownBy(() -> userService.delete(PRINCIPAL, CALLER_ID))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND for an unknown id")
        void delete_unknownId_throwsNotFound() {
            var unknown = UUID.randomUUID();
            when(userRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(PRINCIPAL, unknown))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
            verify(userRepository, never()).delete(any());
        }
    }
}
