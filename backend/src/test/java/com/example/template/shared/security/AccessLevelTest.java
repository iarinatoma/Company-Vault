package com.example.template.shared.security;

import static com.example.template.shared.security.AccessLevel.MANAGE;
import static com.example.template.shared.security.AccessLevel.NO_ACCESS;
import static com.example.template.shared.security.AccessLevel.READ;
import static com.example.template.shared.security.AccessLevel.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccessLevel")
class AccessLevelTest {

    @Test
    @DisplayName("is ordered NO_ACCESS < READ < WRITE < MANAGE")
    void levels_areOrderedFromLeastToMost() {
        assertThat(AccessLevel.values()).containsExactly(NO_ACCESS, READ, WRITE, MANAGE);
    }

    @Test
    @DisplayName("every level includes itself and all lower levels")
    void includes_itselfAndLowerLevels() {
        assertThat(MANAGE.includes(MANAGE)).isTrue();
        assertThat(MANAGE.includes(WRITE)).isTrue();
        assertThat(MANAGE.includes(READ)).isTrue();
        assertThat(WRITE.includes(READ)).isTrue();
        assertThat(READ.includes(READ)).isTrue();
        assertThat(READ.includes(NO_ACCESS)).isTrue();
    }

    @Test
    @DisplayName("no level includes a higher one")
    void doesNotInclude_higherLevels() {
        assertThat(READ.includes(WRITE)).isFalse();
        assertThat(READ.includes(MANAGE)).isFalse();
        assertThat(WRITE.includes(MANAGE)).isFalse();
    }

    @Test
    @DisplayName("NO_ACCESS allows nothing")
    void noAccess_allowsNothing() {
        assertThat(NO_ACCESS.includes(READ)).isFalse();
        assertThat(NO_ACCESS.includes(WRITE)).isFalse();
        assertThat(NO_ACCESS.includes(MANAGE)).isFalse();
    }
}