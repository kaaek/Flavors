package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CurrentUser;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.exception.InvalidCredentialsException;
import com.flavors.exception.SuspendedAccountException;
import com.flavors.repository.AppUserRepository;
import com.flavors.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceImplIT {

    @Autowired
    AuthService authService;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void authenticatesTheRightPasswordAndRole() {
        String email = uniqueEmail();
        users.save(new AppUser(email, passwordEncoder.encode("right-password"), Role.BRAND));

        CurrentUser user = authService.authenticate(email.toUpperCase(), "right-password");

        assertThat(user.getUsername()).isEqualTo(email);
        assertThat(user.getRole()).isEqualTo(Role.BRAND);
    }

    @Test
    void wrongPasswordIsGenericAndAudited() {
        String email = uniqueEmail();
        users.save(new AppUser(email, passwordEncoder.encode("right-password"), Role.CREATOR));

        assertThatThrownBy(() -> authService.authenticate(email, "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("The email or password is incorrect.");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND details->>'email' = ?",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void suspendedAccountCannotLogIn() {
        String email = uniqueEmail();
        long id = users.save(new AppUser(email, passwordEncoder.encode("right-password"), Role.CREATOR)).getId();
        jdbc.update("UPDATE app_user SET status = 'SUSPENDED' WHERE id = ?", id);

        assertThatThrownBy(() -> authService.authenticate(email, "right-password"))
                .isInstanceOf(SuspendedAccountException.class);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
