package com.flavors.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.enums.UserStatus;
import com.flavors.repository.AppUserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Acceptance criterion 4. The seeder already ran when this context started, because seeding is
 * switched on below. This context has its own database, so the first seeding is observable.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "flavors.seed.admin.enabled=true",
        "flavors.admin.email=Seeded.Admin@example.com",
        "flavors.admin.password=correct-horse-battery"
})
class AdminSeederIT {

    @Autowired
    AdminSeeder seeder;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsTheAdminWithAHashedPasswordAndAnAuditEntry() {
        AppUser admin = users.findByEmailIgnoreCase("seeded.admin@example.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isNotEqualTo("correct-horse-battery").startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("correct-horse-battery", admin.getPasswordHash())).isTrue();

        List<Map<String, Object>> audit = jdbc.queryForList(
                "SELECT actor_label, entity_type, entity_id FROM audit_log WHERE action = 'ADMIN_SEEDED'");
        assertThat(audit).singleElement().satisfies(row -> {
            assertThat(row.get("actor_label")).isEqualTo("system");
            assertThat(row.get("entity_type")).isEqualTo("app_user");
            assertThat(row.get("entity_id")).isEqualTo(admin.getId());
        });
    }

    @Test
    void runningAgainChangesNothing() {
        seeder.run(null);
        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email) = 'seeded.admin@example.com'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ADMIN_SEEDED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void failsFastWithoutCredentials() {
        // Plain unit check: the credential test happens before any dependency is touched.
        AdminSeeder noPassword = new AdminSeeder("a@example.com", "", null, null, null, null);
        AdminSeeder noEmail = new AdminSeeder("", "secret", null, null, null, null);

        assertThatThrownBy(() -> noPassword.run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> noEmail.run(null)).isInstanceOf(IllegalStateException.class);
    }
}
