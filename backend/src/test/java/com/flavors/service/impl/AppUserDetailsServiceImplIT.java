package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Loading accounts from app_user for login. Each test uses its own random email. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AppUserDetailsServiceImplIT {

    @Autowired
    AppUserDetailsServiceImpl service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loadsUserByEmailIgnoringCase() {
        String email = UUID.randomUUID() + "@example.com";
        long id = insertUser(email, "BRAND", "ACTIVE");

        CurrentUser user = service.loadUserByUsername(email.toUpperCase());

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo(email);
        assertThat(user.getRole()).isEqualTo(Role.BRAND);
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_BRAND");
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void suspendedUserLoadsDisabled() {
        String email = UUID.randomUUID() + "@example.com";
        insertUser(email, "CREATOR", "SUSPENDED");

        assertThat(service.loadUserByUsername(email).isEnabled()).isFalse();
    }

    @Test
    void unknownEmailIsNotFound() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private long insertUser(String email, String role, String status) {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'x', ?, ?) RETURNING id",
                Long.class, email, role, status);
    }
}
