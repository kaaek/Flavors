package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.dto.BrandRegistrationRequest;
import com.flavors.dto.BrandRegistrationResponse;
import com.flavors.enums.Role;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.BranchRepository;
import com.flavors.service.BrandRegistrationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrandRegistrationServiceImplIT {

    @Autowired
    BrandRegistrationService registration;

    @Autowired
    AppUserRepository users;

    @Autowired
    BrandRepository brands;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAnActiveBrandWithAllBranchesAndAHashedPassword() {
        String email = UUID.randomUUID() + "@example.com";

        BrandRegistrationResponse response = registration.register(request(email, List.of(
                new BranchRegistrationRequest("Hamra", "Beirut"),
                new BranchRegistrationRequest("Jounieh", "Keserwan"))));

        assertThat(response.role()).isEqualTo(Role.BRAND);
        var user = users.findById(response.id()).orElseThrow();
        assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches("correct horse", user.getPasswordHash())).isTrue();
        assertThat(brands.findById(response.id())).get().extracting("businessName").isEqualTo("Karim's Kitchen");
        assertThat(branches.findByBrandId(response.id())).extracting("name").containsExactlyInAnyOrder("Hamra", "Jounieh");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'BRAND_REGISTERED' AND entity_id = ?",
                Integer.class, response.id())).isEqualTo(1);
    }

    @Test
    void duplicateEmailCreatesNoSecondAccount() {
        String email = UUID.randomUUID() + "@example.com";
        registration.register(request(email, List.of(new BranchRegistrationRequest("Hamra", "Beirut"))));

        assertThatThrownBy(() -> registration.register(
                request(email.toUpperCase(), List.of(new BranchRegistrationRequest("Other", "Tripoli")))))
                .isInstanceOf(DuplicateEmailException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email) = lower(?)",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void noBranchCreatesNoAccount() {
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> registration.register(request(email, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one branch is required.");

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    private static BrandRegistrationRequest request(String email, List<BranchRegistrationRequest> branchList) {
        return new BrandRegistrationRequest("Karim's Kitchen", "Karim", email, "+961 1 234567",
                "correct horse", branchList);
    }
}
