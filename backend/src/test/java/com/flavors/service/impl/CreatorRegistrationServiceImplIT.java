package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CreatorRegistrationRequest;
import com.flavors.dto.CreatorRegistrationResponse;
import com.flavors.dto.SocialAccountRegistrationRequest;
import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.CreatorRegistrationService;
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
class CreatorRegistrationServiceImplIT {

    @Autowired
    CreatorRegistrationService registration;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository creators;

    @Autowired
    SocialAccountRepository socialAccounts;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test // TC-F19.1.1
    void createsAPendingCreatorInTheAdminQueue() {
        String email = UUID.randomUUID() + "@example.com";

        CreatorRegistrationResponse response = registration.register(request(email, 10_000, 30_000, social()));

        assertThat(response.role()).isEqualTo(Role.CREATOR);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
        var user = users.findById(response.id()).orElseThrow();
        assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches("correct horse", user.getPasswordHash())).isTrue();
        var profile = creators.findById(response.id()).orElseThrow();
        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
        assertThat(profile.getStorefrontSlug()).isNull(); // no storefront until approved (FR-F19.4)
        assertThat(profile.getBio()).isEqualTo("I eat for a living.");
        assertThat(socialAccounts.findByCreatorId(response.id())).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'CREATOR_REGISTERED' AND entity_id = ?",
                Integer.class, response.id())).isEqualTo(1);
    }

    @Test // TC-F19.1.3
    void duplicateEmailCreatesNoSecondAccount() {
        String email = UUID.randomUUID() + "@example.com";
        registration.register(request(email, 10_000, 30_000, social()));

        assertThatThrownBy(() -> registration.register(request(email.toUpperCase(), 10_000, 30_000, social())))
                .isInstanceOf(DuplicateEmailException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email) = lower(?)",
                Integer.class, email)).isEqualTo(1);
    }

    @Test // TC-F19.1.4
    void minimumPriceAboveMaximumCreatesNoAccount() {
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> registration.register(request(email, 30_000, 10_000, social())))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Minimum price can't be above the maximum price.");

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test // TC-F19.1.5
    void noSocialAccountCreatesNoAccount() {
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> registration.register(request(email, 10_000, 30_000, List.of())))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Connect at least one social account.");

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void aSocialAccountAlreadyUsedByAnotherCreatorRollsEverythingBack() {
        List<SocialAccountRegistrationRequest> shared = social();
        registration.register(request(UUID.randomUUID() + "@example.com", 10_000, 30_000, shared));
        String second = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> registration.register(request(second, 10_000, 30_000, shared)))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("This social account is already connected to another creator.");

        assertThat(users.findByEmailIgnoreCase(second)).isEmpty();
    }

    private static List<SocialAccountRegistrationRequest> social() {
        return List.of(new SocialAccountRegistrationRequest("instagram", "lara." + UUID.randomUUID()));
    }

    private static CreatorRegistrationRequest request(String email, long min, long max,
                                                      List<SocialAccountRegistrationRequest> accounts) {
        return new CreatorRegistrationRequest("Lara", email, "correct horse", "Street food", "Beirut",
                min, max, "I eat for a living.", accounts);
    }
}
