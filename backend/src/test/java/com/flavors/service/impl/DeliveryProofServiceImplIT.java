package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.DeliveryProofResponse;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofUpload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeliveryProofServiceImplIT {

    @TempDir
    static Path proofDirectory;

    @DynamicPropertySource
    static void proofStorage(DynamicPropertyRegistry properties) {
        // A fresh directory avoids collisions with objects left by an earlier throwaway database.
        properties.add("flavors.proof-storage.filesystem-root", () -> proofDirectory.toString());
    }

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01
    };

    @Autowired
    DeliveryProofService proofs;

    @Autowired
    JdbcTemplate jdbc;

    @Test // TC-F5.1.2
    void aDraftIsKeptForTheCreatorAndHiddenFromTheBrandAndAdmin() {
        Fixture fixture = newFixture();

        DeliveryProofResponse creatorView = proofs.saveDraft(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("not final yet"), null), List.of(png("draft.png")));

        assertThat(creatorView.submissions()).singleElement().satisfies(draft -> {
            assertThat(draft.status()).isEqualTo(ProofSubmissionStatus.DRAFT);
            assertThat(draft.round()).isEqualTo(1);
            assertThat(draft.links()).containsExactly("not final yet");
            assertThat(draft.files()).hasSize(1);
        });
        assertThat(proofs.getForBrand(fixture.brandId(), fixture.campaignId()).submissions()).isEmpty();
        assertThat(proofs.getForAdmin(fixture.campaignId()).submissions()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'PROOF_DRAFT_SAVED' AND entity_id = ?",
                Integer.class, creatorView.submissions().getFirst().id())).isEqualTo(1);
    }

    @Test // TC-F5.1.1
    void finalSubmissionIsImmediatelyVisibleAndStartsTheFiveDayReviewTimer() {
        Fixture fixture = newFixture();
        Instant before = Instant.now();

        DeliveryProofResponse submitted = proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("https://www.instagram.com/p/example/"), null),
                List.of(png("proof.png")));

        assertThat(submitted.campaignStatus()).isEqualTo(CampaignStatus.PROOF_SUBMITTED);
        assertThat(submitted.reviewDeadline()).isBetween(before.plus(Duration.ofDays(5)),
                Instant.now().plus(Duration.ofDays(5)).plusSeconds(1));
        assertThat(submitted.submissions()).singleElement().satisfies(version -> {
            assertThat(version.status()).isEqualTo(ProofSubmissionStatus.SUBMITTED);
            assertThat(version.submittedAt()).isNotNull();
            assertThat(version.files()).hasSize(1);
            assertThat(proofs.download(fixture.creatorId(), Role.CREATOR, version.files().getFirst().id()).bytes())
                    .isEqualTo(PNG);
            assertThat(proofs.download(fixture.brandId(), Role.BRAND, version.files().getFirst().id()).bytes())
                    .isEqualTo(PNG);
            assertThat(proofs.download(fixture.adminId(), Role.ADMIN, version.files().getFirst().id()).bytes())
                    .isEqualTo(PNG);
        });
        assertThat(proofs.getForBrand(fixture.brandId(), fixture.campaignId()).submissions())
                .singleElement().extracting(DeliveryProofResponse.Submission::status)
                .isEqualTo(ProofSubmissionStatus.SUBMITTED);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'PROOF_SUBMITTED' AND entity_id = ?",
                Integer.class, submitted.submissions().getFirst().id())).isEqualTo(1);
    }

    @Test // TC-F5.1.3
    void finalSubmissionRejectsMissingOrInvalidLinksWithoutChangingTheCampaign() {
        Fixture missing = newFixture();
        assertThatThrownBy(() -> proofs.submit(missing.creatorId(), missing.campaignId(),
                new ProofDraftRequest(List.of(), null), List.of()))
                .isInstanceOf(InvalidDeliveryProofException.class)
                .hasMessageContaining("at least one link");
        assertCampaignStillActiveWithoutProof(missing.campaignId());

        Fixture invalid = newFixture();
        assertThatThrownBy(() -> proofs.submit(invalid.creatorId(), invalid.campaignId(),
                new ProofDraftRequest(List.of("instagram dot com"), null), List.of()))
                .isInstanceOf(InvalidDeliveryProofException.class)
                .hasMessageContaining("valid http or https URL");
        assertCampaignStillActiveWithoutProof(invalid.campaignId());
    }

    @Test // TC-F5.1.4
    void aCorrectionRoundCreatesANewVersionAndKeepsTheFirstVersion() {
        Fixture fixture = newFixture();
        proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("https://example.com/version-1"), null), List.of());
        jdbc.update("UPDATE campaign SET status = 'CORRECTIONS_REQUESTED', correction_rounds_used = 1 WHERE id = ?",
                fixture.campaignId());

        DeliveryProofResponse result = proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("https://example.com/version-2"), null), List.of());

        assertThat(result.submissions()).extracting(DeliveryProofResponse.Submission::round)
                .containsExactly(1, 2);
        assertThat(result.submissions()).extracting(DeliveryProofResponse.Submission::status)
                .containsOnly(ProofSubmissionStatus.SUBMITTED);
        assertThat(result.submissions().get(0).links()).containsExactly("https://example.com/version-1");
        assertThat(result.submissions().get(1).links()).containsExactly("https://example.com/version-2");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM proof_submission WHERE campaign_id = ? AND status = 'SUBMITTED'",
                Integer.class, fixture.campaignId())).isEqualTo(2);
    }

    @Test
    void onlyTheAssignedCreatorAndBrandCanSeeTheirProof() {
        Fixture fixture = newFixture();
        Fixture strangers = newFixture();
        DeliveryProofResponse result = proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("https://example.com/post"), null), List.of(png("proof.png")));
        long fileId = result.submissions().getFirst().files().getFirst().id();

        assertThatThrownBy(() -> proofs.getForCreator(strangers.creatorId(), fixture.campaignId()))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> proofs.getForBrand(strangers.brandId(), fixture.campaignId()))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> proofs.download(strangers.creatorId(), Role.CREATOR, fileId))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThatThrownBy(() -> proofs.download(strangers.brandId(), Role.BRAND, fileId))
                .isInstanceOf(DeliveryProofNotFoundException.class);
    }

    @Test
    void replacingADraftScreenshotDeletesOnlyTheRemovedPrivateObject() {
        Fixture fixture = newFixture();
        DeliveryProofResponse first = proofs.saveDraft(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("https://example.com/post"), null), List.of(png("old.png")));
        long oldFileId = first.submissions().getFirst().files().getFirst().id();

        DeliveryProofResponse replaced = proofs.saveDraft(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(null, List.of()), List.of(png("new.png")));

        long newFileId = replaced.submissions().getFirst().files().getFirst().id();
        assertThat(newFileId).isNotEqualTo(oldFileId);
        assertThatThrownBy(() -> proofs.download(fixture.creatorId(), Role.CREATOR, oldFileId))
                .isInstanceOf(DeliveryProofNotFoundException.class);
        assertThat(proofs.download(fixture.creatorId(), Role.CREATOR, newFileId).bytes()).isEqualTo(PNG);
    }

    @Test
    void aFailedFinalSubmissionRollsBackItsDatabaseRowsAndUploadedObject() throws Exception {
        Fixture fixture = newFixture();
        Path campaignObjects = proofDirectory.resolve("proof").resolve(Long.toString(fixture.campaignId()));

        assertThatThrownBy(() -> proofs.submit(fixture.creatorId(), fixture.campaignId(),
                new ProofDraftRequest(List.of("bad link"), null), List.of(png("rollback.png"))))
                .isInstanceOf(InvalidDeliveryProofException.class);

        assertCampaignStillActiveWithoutProof(fixture.campaignId());
        if (Files.exists(campaignObjects)) {
            try (var objects = Files.walk(campaignObjects)) {
                assertThat(objects.filter(Files::isRegularFile)).isEmpty();
            }
        }
    }

    @Test
    void concurrentFinalSubmissionsProduceExactlyOneVersion() throws Exception {
        Fixture fixture = newFixture();
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> submitAfter(start, fixture));
        var second = CompletableFuture.supplyAsync(() -> submitAfter(start, fixture));
        start.countDown();

        List<Object> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

        assertThat(outcomes.stream().filter(DeliveryProofResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(DeliveryProofConflictException.class::isInstance)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM proof_submission WHERE campaign_id = ? AND status = 'SUBMITTED'",
                Integer.class, fixture.campaignId())).isEqualTo(1);
    }

    @Test
    void limitsScreenshotsAndNeverTrustsADeclaredImageType() {
        Fixture tooMany = newFixture();
        List<ProofUpload> six = List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"), png("5.png"),
                png("6.png"));
        assertThatThrownBy(() -> proofs.saveDraft(tooMany.creatorId(), tooMany.campaignId(),
                new ProofDraftRequest(null, null), six))
                .isInstanceOf(InvalidDeliveryProofException.class)
                .hasMessageContaining("at most 5");

        Fixture spoofed = newFixture();
        ProofUpload fake = new ProofUpload("fake.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> proofs.saveDraft(spoofed.creatorId(), spoofed.campaignId(),
                new ProofDraftRequest(null, null), List.of(fake)))
                .isInstanceOf(InvalidDeliveryProofException.class)
                .hasMessageContaining("PNG, JPEG, GIF or WebP");
    }

    private Object submitAfter(CountDownLatch start, Fixture fixture) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return proofs.submit(fixture.creatorId(), fixture.campaignId(),
                    new ProofDraftRequest(List.of("https://example.com/post"), null), List.of());
        } catch (DeliveryProofConflictException e) {
            return e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertCampaignStillActiveWithoutProof(long campaignId) {
        assertThat(jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, campaignId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proof_submission WHERE campaign_id = ?",
                Integer.class, campaignId)).isZero();
    }

    private Fixture newFixture() {
        long brandId = insertUser("BRAND");
        long creatorId = insertUser("CREATOR");
        long adminId = insertUser("ADMIN");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, ?, ?, ?)",
                brandId, "Karim's Kitchen", "Karim", "+9611234567");
        jdbc.update("""
                INSERT INTO creator_profile
                    (user_id, display_name, food_category, location, price_min_cents, price_max_cents)
                VALUES (?, 'Lara', 'Street food', 'Beirut', 10000, 30000)
                """, creatorId);
        Long campaignId = jdbc.queryForObject("""
                INSERT INTO campaign
                    (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                     start_date, last_posting_date)
                VALUES (?, ?, ?, 'ACTIVE', 20000, 20000, current_date - 2, current_date + 2)
                RETURNING id
                """, Long.class, brandId, creatorId, "Launch " + UUID.randomUUID());
        return new Fixture(brandId, creatorId, adminId, campaignId);
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role)
                VALUES (?, '{noop}x', ?)
                RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com", role);
    }

    private static ProofUpload png(String filename) {
        return new ProofUpload(filename, "image/png", PNG);
    }

    private record Fixture(long brandId, long creatorId, long adminId, long campaignId) {
    }
}
