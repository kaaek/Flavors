package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CreatorRegistrationRequest;
import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.SocialAccountRegistrationRequest;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.CreatorRegistrationService;
import com.flavors.service.CreatorReviewService;
import com.flavors.service.CreatorStatusService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreatorReviewServiceImplIT {

    @Autowired
    CreatorReviewService reviews;

    @Autowired
    CreatorRegistrationService registration;

    @Autowired
    CreatorStatusService statuses;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository creators;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theQueueListsPendingCreators() {
        long creatorId = newCreator();

        assertThat(reviews.listPending()).extracting("id").contains(creatorId);
    }

    @Test // FR-F20.2
    void theAdminSeesRegistrationDetailsAndSocialAccounts() {
        long creatorId = newCreator();

        CreatorReviewResponse review = reviews.get(creatorId);

        assertThat(review.displayName()).isEqualTo("Lara");
        assertThat(review.priceMinCents()).isEqualTo(10_000);
        assertThat(review.socialAccounts()).hasSize(1);
        assertThat(review.needsReconnection()).isFalse();
    }

    @Test // TC-F20.1.1
    void approvingVerifiesTheCreatorGivesAStorefrontAndStoresWhoAndWhen() {
        long creatorId = newCreator();
        long adminId = newAdmin();

        CreatorReviewResponse review = reviews.approve(adminId, creatorId);

        assertThat(review.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        var profile = creators.findById(creatorId).orElseThrow();
        assertThat(profile.getStorefrontSlug()).isEqualTo("lara-" + creatorId);
        assertThat(profile.getReviewedBy()).isEqualTo(adminId);
        assertThat(profile.getReviewedAt()).isNotNull();
        assertThat(reviews.listPending()).extracting("id").doesNotContain(creatorId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'CREATOR_APPROVED' AND entity_id = ?",
                Integer.class, creatorId)).isEqualTo(1);
    }

    @Test // TC-F20.1.2
    void rejectingNeedsAReasonAndTheCreatorSeesIt() {
        long creatorId = newCreator();
        long adminId = newAdmin();

        assertThatThrownBy(() -> reviews.reject(adminId, creatorId, " "))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(creators.findById(creatorId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.PENDING_APPROVAL);

        reviews.reject(adminId, creatorId, "Account is private.");

        var status = statuses.statusOf(creatorId);
        assertThat(status.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(status.rejectionReason()).isEqualTo("Account is private.");
        assertThat(creators.findById(creatorId).orElseThrow().getStorefrontSlug()).isNull();
    }

    @Test // TC-F20.1.3
    void approvalIsBlockedWhileTheSocialAccountNeedsReconnection() {
        long creatorId = newCreator();
        long adminId = newAdmin();
        jdbc.update("UPDATE social_account SET status = 'NEEDS_RECONNECTION' WHERE creator_id = ?", creatorId);

        assertThat(reviews.get(creatorId).needsReconnection()).isTrue();
        assertThatThrownBy(() -> reviews.approve(adminId, creatorId))
                .isInstanceOf(ReviewNotAllowedException.class)
                .hasMessageContaining("Needs reconnection");

        assertThat(creators.findById(creatorId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.PENDING_APPROVAL);
    }

    @Test // TC-F20.1.4
    void anUnreviewedCreatorStaysPending() {
        long creatorId = newCreator();

        assertThat(statuses.statusOf(creatorId).verificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
        assertThat(creators.findById(creatorId).orElseThrow().getStorefrontSlug()).isNull();
    }

    @Test
    void aProfileCanBeReviewedOnlyOnce() {
        long creatorId = newCreator();
        long adminId = newAdmin();
        reviews.approve(adminId, creatorId);

        assertThatThrownBy(() -> reviews.reject(adminId, creatorId, "Changed my mind"))
                .isInstanceOf(ReviewNotAllowedException.class);
    }

    @Test
    void unknownCreatorIsNotFound() {
        assertThatThrownBy(() -> reviews.get(-1)).isInstanceOf(CreatorNotFoundException.class);
    }

    private long newCreator() {
        return registration.register(new CreatorRegistrationRequest("Lara", UUID.randomUUID() + "@example.com",
                "correct horse", "Street food", "Beirut", 10_000L, 30_000L, null,
                List.of(new SocialAccountRegistrationRequest("instagram", "lara." + UUID.randomUUID())))).id();
    }

    private long newAdmin() {
        return users.saveAndFlush(new AppUser(UUID.randomUUID() + "@admin.example.com", "{noop}x", Role.ADMIN)).getId();
    }
}
