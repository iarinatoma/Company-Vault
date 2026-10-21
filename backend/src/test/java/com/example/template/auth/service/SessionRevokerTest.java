package com.example.template.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.example.template.auth.model.RefreshToken;
import com.example.template.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionRevoker")
class SessionRevokerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private SessionRevoker sessionRevoker;

    private static final UUID FAMILY_ID = UUID.randomUUID();

    private static RefreshToken activeToken() {
        var token = new RefreshToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash(UUID.randomUUID().toString().replace("-", ""));
        token.setFamilyId(FAMILY_ID);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    @Nested
    @DisplayName("revokeFamily")
    class RevokeFamilyTests {

        @Test
        @DisplayName("marks every active token of the family as revoked")
        void revokeFamily_activeTokens_revokesAll() {
            var first = activeToken();
            var second = activeToken();
            when(refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(FAMILY_ID))
                    .thenReturn(List.of(first, second));

            sessionRevoker.revokeFamily(FAMILY_ID);

            assertThat(first.getRevokedAt()).isNotNull();
            assertThat(second.getRevokedAt()).isNotNull();
        }

        @Test
        @DisplayName("completes without error when the family has no active tokens")
        void revokeFamily_noActiveTokens_completesWithoutError() {
            when(refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(FAMILY_ID))
                    .thenReturn(List.of());

            assertThatCode(() -> sessionRevoker.revokeFamily(FAMILY_ID)).doesNotThrowAnyException();
        }
    }
}
