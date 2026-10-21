package com.example.template.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.template.shared.security.Role;
import com.example.template.user.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mapper unit tests mock nothing — the generated implementation runs as-is.
 */
@DisplayName("UserMapper")
class UserMapperTest {

    private final UserMapper userMapper = new UserMapperImpl();

    @Nested
    @DisplayName("toResponse")
    class ToResponseTests {

        @Test
        @DisplayName("maps every exposed field and nothing sensitive")
        void toResponse_populatedUser_mapsExposedFields() {
            var user = new User();
            user.setEmail("someone@example.com");
            user.setPasswordHash("{bcrypt}secret");
            user.setRole(Role.MANAGER);
            user.setTwoFactorEnabled(true);

            var response = userMapper.toResponse(user);

            assertThat(response.email()).isEqualTo("someone@example.com");
            assertThat(response.role()).isEqualTo(Role.MANAGER);
            assertThat(response.twoFactorEnabled()).isTrue();
        }
    }
}
