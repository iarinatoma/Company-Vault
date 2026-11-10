package com.example.template.access.service;

import static com.example.template.shared.security.AccessLevel.MANAGE;
import static com.example.template.shared.security.AccessLevel.NO_ACCESS;
import static com.example.template.shared.security.AccessLevel.READ;
import static com.example.template.shared.security.AccessLevel.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.template.access.model.AccessGrant;
import com.example.template.access.repository.AccessGrantRepository;
import com.example.template.shared.error.ApiException;
import com.example.template.shared.error.ErrorCode;
import com.example.template.shared.security.AccessLevel;
import com.example.template.shared.security.AuthPrincipal;
import com.example.template.shared.security.ResourceRef;
import com.example.template.shared.security.ResourceType;
import com.example.template.shared.security.Role;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControl")
class AccessControlTest {

    @Mock
    private AccessGrantRepository accessGrantRepository;

    @InjectMocks
    private AccessControl accessControl;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final ResourceRef VAULT = new ResourceRef(ResourceType.VAULT, UUID.randomUUID());

    private static AuthPrincipal principal(Role role) {
        return new AuthPrincipal(USER_ID, "someone@example.com", role);
    }

    private static AccessGrant grant(AccessLevel level) {
        var grant = new AccessGrant();
        grant.setUserId(USER_ID);
        grant.setResourceType(VAULT.type());
        grant.setResourceId(VAULT.id());
        grant.setAccessLevel(level);
        return grant;
    }

    private void storedGrant(AccessLevel level) {
        when(accessGrantRepository.findByUserIdAndResourceTypeAndResourceId(USER_ID, VAULT.type(), VAULT.id()))
                .thenReturn(Optional.of(grant(level)));
    }

    private void noStoredGrant() {
        when(accessGrantRepository.findByUserIdAndResourceTypeAndResourceId(USER_ID, VAULT.type(), VAULT.id()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("levelOf")
    class LevelOf {

        @Test
        @DisplayName("is NO_ACCESS when the user has no grant on the resource")
        void noGrant_isNoAccess() {
            noStoredGrant();

            assertThat(accessControl.levelOf(principal(Role.MEMBER), VAULT)).isEqualTo(NO_ACCESS);
        }

        @Test
        @DisplayName("is the stored level when the user has a grant")
        void grant_isStoredLevel() {
            storedGrant(WRITE);

            assertThat(accessControl.levelOf(principal(Role.MEMBER), VAULT)).isEqualTo(WRITE);
        }

        @Test
        @DisplayName("is always NO_ACCESS for an auditor, even with a stored grant")
        void auditor_isAlwaysNoAccess() {
            assertThat(accessControl.levelOf(principal(Role.AUDITOR), VAULT)).isEqualTo(NO_ACCESS);
            verify(accessGrantRepository, never()).findByUserIdAndResourceTypeAndResourceId(any(), any(), any());
        }

        @Test
        @DisplayName("gives an administrator no automatic access — admins need a grant too")
        void administrator_withoutGrant_isNoAccess() {
            noStoredGrant();

            assertThat(accessControl.levelOf(principal(Role.ADMINISTRATOR), VAULT)).isEqualTo(NO_ACCESS);
        }
    }

    @Nested
    @DisplayName("require")
    class Require {

        @Test
        @DisplayName("passes when the stored level is exactly the required one")
        void sameLevel_passes() {
            storedGrant(WRITE);

            assertThatCode(() -> accessControl.require(principal(Role.MEMBER), VAULT, WRITE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passes when the stored level is higher than required")
        void higherLevel_passes() {
            storedGrant(MANAGE);

            assertThatCode(() -> accessControl.require(principal(Role.MEMBER), VAULT, READ))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws FORBIDDEN when the stored level is lower than required")
        void lowerLevel_throwsForbidden() {
            storedGrant(READ);

            assertThatThrownBy(() -> accessControl.require(principal(Role.MEMBER), VAULT, WRITE))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("throws FORBIDDEN without any grant — no access to someone else's resource")
        void noGrant_throwsForbidden() {
            noStoredGrant();

            assertThatThrownBy(() -> accessControl.require(principal(Role.MEMBER), VAULT, READ))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).errorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("grant and revoke")
    class GrantAndRevoke {

        @Test
        @DisplayName("creates a new grant when the user has none yet")
        void grant_new_isSaved() {
            noStoredGrant();
            var saved = ArgumentCaptor.forClass(AccessGrant.class);

            accessControl.grant(USER_ID, VAULT, MANAGE);

            verify(accessGrantRepository).save(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getValue().getResourceType()).isEqualTo(ResourceType.VAULT);
            assertThat(saved.getValue().getResourceId()).isEqualTo(VAULT.id());
            assertThat(saved.getValue().getAccessLevel()).isEqualTo(MANAGE);
        }

        @Test
        @DisplayName("replaces the level of an existing grant instead of adding a second row")
        void grant_existing_isUpdated() {
            var existing = grant(READ);
            when(accessGrantRepository.findByUserIdAndResourceTypeAndResourceId(USER_ID, VAULT.type(), VAULT.id()))
                    .thenReturn(Optional.of(existing));

            accessControl.grant(USER_ID, VAULT, WRITE);

            verify(accessGrantRepository).save(existing);
            assertThat(existing.getAccessLevel()).isEqualTo(WRITE);
        }

        @Test
        @DisplayName("granting NO_ACCESS removes the grant")
        void grant_noAccess_revokes() {
            var existing = grant(WRITE);
            when(accessGrantRepository.findByUserIdAndResourceTypeAndResourceId(USER_ID, VAULT.type(), VAULT.id()))
                    .thenReturn(Optional.of(existing));

            accessControl.grant(USER_ID, VAULT, NO_ACCESS);

            verify(accessGrantRepository).delete(existing);
            verify(accessGrantRepository, never()).save(any());
        }

        @Test
        @DisplayName("revoking without a grant does nothing")
        void revoke_withoutGrant_doesNothing() {
            noStoredGrant();

            accessControl.revoke(USER_ID, VAULT);

            verify(accessGrantRepository, never()).delete(any());
        }
    }
}