package com.shardinglab.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void newUserHasNoCreationTime() {
        User user = User.newUser(42L, "Ada", "ada@example.com");

        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.createdAt()).isNull();
    }

    @Test
    void rejectsBlankNameAndEmail() {
        assertThatThrownBy(() -> User.newUser(1L, " ", "a@example.com"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> User.newUser(1L, "Ada", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
    }
}
