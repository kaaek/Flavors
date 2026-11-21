package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.dto.ProofReviewRequest;
import com.flavors.dto.ProofReviewResponse;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import com.flavors.enums.ProofReviewDecision;
import com.flavors.enums.ProofReviewFollowUp;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.ProofReviewRepository;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofReviewService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real PostgreSQL mappings, transactions and races; CI runs these with Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProofReviewServiceImplIT {

    @Autowired
    ProofReviewService reviews;

    @Autowired
    DeliveryProofService proofs;

    @Autowired
    ProofReviewRepository reviewRows;

    @Autowired
    JdbcTemplate jdbc;

    @Test // TC-F6.1.1: decision timing is tested; actual release belongs to UC-F7.2.
    void approvalIsPersistedWithinFiveSecondsWithoutClaimingPaymentWasReleased() {
        Fixture fixture = newFixture();

        var result = assertTimeout(Duration.ofSeconds(5), () -> review(fixture, ProofReviewDecision.APPROVED, null));

        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.RELEASE_PAYMENT);
        assertThat(reviewRows.findBySubmissionId(fixture.submissionId())).isPresent();
        assertThat(reviews.history(fixture.brandId(), Role.BRAND, fixture.campaignId()).availableDecisions()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT proof_review_deadline FROM campaign WHERE id = ?",
                java.sql.Timestamp.class, fixture.campaignId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE campaign_id = ?",
                String.class, fixture.campaignId())).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PROOF_APPROVED' AND entity_id = ?",
                Integer.class, result.id())).isEqualTo(1);
    }

    @Test // FR-F6.6: a rejection is recorded within five seconds too; the dispute itself belongs to UC-F8.1.
    void rejectionIsPersistedWithinFiveSecondsWithoutClaimingADisputeWasOpened() {
        Fixture fixture = newFixture();

        var result = assertTimeout(Duration.ofSeconds(5),
                () -> review(fixture, ProofReviewDecision.REJECTED, "The reel was never posted"));

        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.FREEZE_PAYMENT_AND_OPEN_DISPUTE);
        assertThat(reviewRows.findBySubmissionId(fixture.submissionId())).hasValueSatisfying(saved -> {
            assertThat(saved.getDecision()).isEqualTo(ProofReviewDecision.REJECTED);
            assertThat(saved.getReason()).isEqualTo("The reel was never posted");
        });
        assertThat(reviews.history(fixture.brandId(), Role.BRAND, fixture.campaignId()).availableDecisions()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE campaign_id = ?",
                String.class, fixture.campaignId())).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispute WHERE campaign_id = ?",
                Integer.class, fixture.campaignId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PROOF_REJECTED' AND entity_id = ?",
                Integer.class, result.id())).isEqualTo(1);
    }

    @Test // TC-F6.1.2 and TC-F6.1.3
    void twoCorrectionsAllowThreeImmutableSubmissionsAndNoThirdCorrection() {
        Fixture fixture = newFixture();
        review(fixture, ProofReviewDecision.CORRECTIONS_REQUESTED, "Fix caption");
        var afterFirst = reviews.history(fixture.creatorId(), Role.CREATOR, fixture.campaignId());
        assertThat(afterFirst.campaignStatus()).isEqualTo(CampaignStatus.CORRECTIONS_REQUESTED);
        assertThat(afterFirst.correctionRoundsUsed()).isEqualTo(1);
        assertThat(afterFirst.reviewDeadline()).isNull();
        assertThat(afterFirst.reviews().getFirst().reason()).isEqualTo("Fix caption");

        Fixture second = resubmit(fixture, "https://example.com/second");
        review(second, ProofReviewDecision.CORRECTIONS_REQUESTED, "One last correction");
        Fixture third = resubmit(fixture, "https://example.com/third");

        assertThat(reviews.history(third.brandId(), Role.BRAND, third.campaignId()).availableDecisions())
                .containsExactly(ProofReviewDecision.APPROVED, ProofReviewDecision.REJECTED);
        assertThatThrownBy(() -> review(third, ProofReviewDecision.CORRECTIONS_REQUESTED, "Fourth version"))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThat(proofs.getForCreator(third.creatorId(), third.campaignId()).submissions()).hasSize(3);
        assertThat(reviewRows.findByCampaignId(third.campaignId())).hasSize(2);
        review(third, ProofReviewDecision.APPROVED, null);
        assertThat(reviewRows.findByCampaignId(third.campaignId())).hasSize(3);
    }

    @Test // TC-F6.1.4: reason/record only; freezing and opening the dispute are explicitly deferred.
    void rejectionRequiresAReasonAndPersistsTheRequiredDisputeFollowUp() {
        Fixture fixture = newFixture();

        assertThatThrownBy(() -> review(fixture, ProofReviewDecision.REJECTED, " "))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(reviewRows.findBySubmissionId(fixture.submissionId())).isEmpty();

        var result = review(fixture, ProofReviewDecision.REJECTED, "  Wrong platform  ");

        assertThat(result.reason()).isEqualTo("Wrong platform");
        assertThat(result.requiredFollowUp()).isEqualTo(ProofReviewFollowUp.FREEZE_PAYMENT_AND_OPEN_DISPUTE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispute WHERE campaign_id = ?",
                Integer.class, fixture.campaignId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM escrow_payment WHERE campaign_id = ?",
                String.class, fixture.campaignId())).isEqualTo("HELD");
    }

    @Test
    void identicalConcurrentCorrectionRequestsAreOneDecisionAndOneUsedRound() throws Exception {
        Fixture fixture = newFixture();
        CountDownLatch start = new CountDownLatch(1);
        var first = concurrentReview(start, fixture, ProofReviewDecision.CORRECTIONS_REQUESTED, "Fix caption");
        var second = concurrentReview(start, fixture, ProofReviewDecision.CORRECTIONS_REQUESTED, "Fix caption");
        start.countDown();

        Object firstResult = first.get(15, TimeUnit.SECONDS);
        Object secondResult = second.get(15, TimeUnit.SECONDS);

        assertThat(firstResult).isInstanceOf(ProofReviewResponse.class).isEqualTo(secondResult);
        assertThat(reviewRows.findByCampaignId(fixture.campaignId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT correction_rounds_used FROM campaign WHERE id = ?",
                Integer.class, fixture.campaignId())).isEqualTo(1);
    }

    @Test
    void conflictingConcurrentDecisionsNeverBothSucceed() throws Exception {
        Fixture fixture = newFixture();
        CountDownLatch start = new CountDownLatch(1);
        var approve = concurrentReview(start, fixture, ProofReviewDecision.APPROVED, null);
        var reject = concurrentReview(start, fixture, ProofReviewDecision.REJECTED, "Missing post");
        start.countDown();

        List<Object> outcomes = List.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS));

        assertThat(outcomes.stream().filter(ProofReviewResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(DeliveryProofConflictException.class::isInstance)).hasSize(1);
        assertThat(reviewRows.findByCampaignId(fixture.campaignId())).hasSize(1);
    }

    @Test // TC-F6.1.5 candidate detection only; no actual release until UC-F7.2 is connected.
    void timerCandidatesExcludeRespondedCorrectingAndFutureCampaignsEvenWithAStaleDeadline() {
        Fixture overdue = newFixture();
        Fixture correcting = newFixture();
        Fixture responded = newFixture();
        Fixture future = newFixture();
        review(correcting, ProofReviewDecision.CORRECTIONS_REQUESTED, null);
        review(responded, ProofReviewDecision.APPROVED, null);
        Instant overdueDeadline = Instant.now().minus(Duration.ofMinutes(5));

        for (Fixture fixture : List.of(overdue, correcting, responded)) {
                int updated = jdbc.update(
                        "UPDATE campaign SET proof_review_deadline = ? WHERE id = ?",
                        java.sql.Timestamp.from(overdueDeadline),
                        fixture.campaignId()
                );

                assertThat(updated).isEqualTo(1);
        }

        assertThat(reviews.findOverdueCampaignIds()).contains(overdue.campaignId())
                .doesNotContain(correcting.campaignId(), responded.campaignId(), future.campaignId());
        assertThatThrownBy(() -> review(overdue, ProofReviewDecision.REJECTED, "Too late"))
                .isInstanceOf(DeliveryProofConflictException.class);
        assertThat(reviewRows.findBySubmissionId(overdue.submissionId())).isEmpty();
    }

    @Test
    void ownershipIsEnforcedForDecisionsAndHistory() {
        Fixture fixture = newFixture();
        Fixture another = newFixture();

        assertThatThrownBy(() -> reviews.review(another.brandId(), fixture.campaignId(), fixture.submissionId(),
                new ProofReviewRequest(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> reviews.history(another.creatorId(), Role.CREATOR, fixture.campaignId()))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> reviews.review(fixture.brandId(), fixture.campaignId(), another.submissionId(),
                new ProofReviewRequest(ProofReviewDecision.APPROVED, null)))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThat(reviewRows.findByCampaignId(fixture.campaignId())).isEmpty();
    }

    @Test
    void agreedTermsAreReadFromTheCampaignWithoutChangingThem() {
        Fixture fixture = newFixture();
        jdbc.update("""
                UPDATE campaign SET content_requirements = 'Mention Hamra', screenshots_required = true WHERE id = ?
                """, fixture.campaignId());
        jdbc.update("""
                INSERT INTO campaign_deliverable (campaign_id, platform, type, count)
                VALUES (?, 'INSTAGRAM', 'REEL', 2)
                """, fixture.campaignId());

        var agreement = reviews.history(fixture.brandId(), Role.BRAND, fixture.campaignId()).agreement();

        assertThat(agreement.startDate()).isNotNull();
        assertThat(agreement.lastPostingDate()).isNotNull();
        assertThat(agreement.contentRequirements()).isEqualTo("Mention Hamra");
        assertThat(agreement.screenshotsRequired()).isTrue();
        assertThat(agreement.deliverables()).singleElement().satisfies(deliverable -> {
            assertThat(deliverable.platform()).isEqualTo(DeliverablePlatform.INSTAGRAM);
            assertThat(deliverable.type()).isEqualTo(DeliverableType.REEL);
            assertThat(deliverable.count()).isEqualTo(2);
        });
    }

    private CompletableFuture<Object> concurrentReview(CountDownLatch start, Fixture fixture,
            ProofReviewDecision decision, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start.");
                }
                return review(fixture, decision, reason);
            } catch (DeliveryProofConflictException e) {
                return e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    private ProofReviewResponse review(Fixture fixture, ProofReviewDecision decision, String reason) {
        return reviews.review(fixture.brandId(), fixture.campaignId(), fixture.submissionId(),
                new ProofReviewRequest(decision, reason));
    }

    private Fixture resubmit(Fixture fixture, String url) {
        var response = proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of(url), null), List.of());
        assertThat(response.reviewDeadline()).isNotNull();
        return new Fixture(fixture.brandId(), fixture.creatorId(), fixture.campaignId(),
                response.submissions().getLast().id());
    }

    private Fixture newFixture() {
        long brandId = user("BRAND");
        long creatorId = user("CREATOR");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Brand', 'Karim', '12345')",
                brandId);
        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Food', 'Beirut', 10000, 30000)
                """, creatorId);
        long campaignId = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents,
                    agreed_price_cents, start_date, last_posting_date)
                VALUES (?, ?, 'Review test', 'ACTIVE', 20000, 20000, current_date - 2, current_date + 2)
                RETURNING id
                """, Long.class, brandId, creatorId);
        jdbc.update("""
                INSERT INTO escrow_payment (campaign_id, status, amount_cents, provider, held_at)
                VALUES (?, 'HELD', 20000, 'test-only', now())
                """, campaignId);
        var response = proofs.submit(creatorId, campaignId,
                new ProofDraftRequest(List.of("https://example.com/post"), null), List.of());
        return new Fixture(brandId, creatorId, campaignId, response.submissions().getFirst().id());
    }

    private long user(String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role) VALUES (?, '{noop}x', ?) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com", role);
    }

    private record Fixture(long brandId, long creatorId, long campaignId, long submissionId) {
    }
}
