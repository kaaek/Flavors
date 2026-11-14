package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.CampaignRequest.DeliverableRequest;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import com.flavors.enums.CampaignStatus;
import com.flavors.exception.CampaignNotEditableException;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignValidationException;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.exception.NoBranchException;
import com.flavors.service.CampaignService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** UC-F3.1 against real Postgres: TC-F3.1.1 to TC-F3.1.4 and EX-F3.1.1 to EX-F3.1.3. Each test makes its own brand and creator. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CampaignServiceImplIT {

    /** The service reads "today" in this zone, so the tests do too. */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Beirut"));

    @Autowired
    CampaignService service;

    @Autowired
    JdbcTemplate jdbc;

    // TC-F3.1.1
    @Test
    void sendingReachesTheCreatorAndStartsTimerA() {
        Brand brand = brand("Hamra", "Achrafieh");
        long creator = creator("APPROVED", "ACTIVE");

        CampaignResponse sent = service.send(brand.id, complete(brand, creator));

        assertThat(sent.status()).isEqualTo(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        assertThat(sent.sentAt()).isNotNull();
        // Timer A is exactly three days after the send.
        assertThat(Duration.between(sent.sentAt(), sent.responseDeadline())).isEqualTo(Duration.ofDays(3));
        assertThat(sent.creator().id()).isEqualTo(creator);
        assertThat(sent.branches()).hasSize(2);
        assertThat(sent.priceOfferedCents()).isEqualTo(25_000);
        assertThat(sent.deliverables()).extracting(d -> d.type().name() + "x" + d.count()).containsExactly("REELx2");

        // The creator is told in the app, about this campaign, and only once.
        List<Map<String, Object>> rows = notificationsOf(creator);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "CAMPAIGN_REQUEST").containsEntry("campaign_id", sent.id())
                .containsEntry("read_at", null);
        assertThat((String) rows.get(0).get("message")).contains("Summer menu launch");

        // The row in the database agrees (a client could not be trusted to show it).
        assertThat(jdbc.queryForMap("SELECT status, sent_at, response_deadline FROM campaign WHERE id = ?", sent.id()))
                .containsEntry("status", "AWAITING_CREATOR_RESPONSE");
        assertThat(audit("CAMPAIGN_SENT", sent.id())).isEqualTo(1);
    }

    // TC-F3.1.2, EX-F3.1.1
    @Test
    void sendingWithAMissingRequiredFieldSendsNothingAndNamesEveryField() {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest empty = new CampaignRequest("Brunch", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.send(brand.id, empty))
                .isInstanceOfSatisfying(CampaignValidationException.class, e -> assertThat(e.getErrors())
                        .containsOnlyKeys("creatorId", "branchIds", "priceOfferedCents", "startDate", "lastPostingDate",
                                "deliverables"));

        assertThat(campaignCount(brand.id)).isZero();
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    void eachRequiredFieldIsRefusedOnItsOwn() {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest ok = complete(brand, creator);

        assertRefused(brand, new CampaignRequest("  ", ok.creatorId(), ok.branchIds(), ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "name");
        assertRefused(brand, new CampaignRequest(ok.name(), null, ok.branchIds(), ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "creatorId");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), List.of(), ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "branchIds");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), ok.branchIds(), null,
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "priceOfferedCents");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), ok.branchIds(), ok.priceOfferedCents(),
                null, ok.lastPostingDate(), ok.deliverables(), null), "startDate");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), ok.branchIds(), ok.priceOfferedCents(),
                ok.startDate(), null, ok.deliverables(), null), "lastPostingDate");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), ok.branchIds(), ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), List.of(), null), "deliverables");
        assertThat(campaignCount(brand.id)).isZero();
    }

    @Test
    void fieldRulesAreEnforced() {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest ok = complete(brand, creator);

        assertRefused(brand, with(ok, "x".repeat(151), ok.priceOfferedCents(), ok.startDate(), ok.lastPostingDate()), "name");
        assertRefused(brand, with(ok, ok.name(), 0L, ok.startDate(), ok.lastPostingDate()), "priceOfferedCents");
        assertRefused(brand, with(ok, ok.name(), -5L, ok.startDate(), ok.lastPostingDate()), "priceOfferedCents");
        assertRefused(brand, with(ok, ok.name(), ok.priceOfferedCents(), TODAY.minusDays(1), TODAY.plusDays(5)), "startDate");
        assertRefused(brand, with(ok, ok.name(), ok.priceOfferedCents(), TODAY.plusDays(5), TODAY.plusDays(4)),
                "lastPostingDate");
        assertRefused(brand, withDeliverables(ok, new DeliverableRequest("INSTAGRAM", "REEL", 0)),
                "deliverables[0].count");
        assertRefused(brand, withDeliverables(ok, new DeliverableRequest("INSTAGRAM", "BLOG", 1)), "deliverables[0].type");
        assertRefused(brand, withDeliverables(ok, new DeliverableRequest("TIKTOK", "REEL", 1)),
                "deliverables[0].platform");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), ok.branchIds(), ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), "x".repeat(2001)), "contentRequirements");
    }

    @Test
    void todayIsAValidStartDateAndTheSameLastPostingDateIsToo() {
        Brand brand = brand("Hamra");
        CampaignRequest ok = complete(brand, creator("APPROVED", "ACTIVE"));

        CampaignResponse sent = service.send(brand.id, with(ok, ok.name(), ok.priceOfferedCents(), TODAY, TODAY));

        assertThat(sent.startDate()).isEqualTo(TODAY);
        assertThat(sent.lastPostingDate()).isEqualTo(TODAY);
    }

    @Test
    void contentRequirementsAreOptional() {
        Brand brand = brand("Hamra");
        CampaignRequest ok = complete(brand, creator("APPROVED", "ACTIVE"));

        CampaignResponse sent = service.send(brand.id, ok);

        assertThat(sent.contentRequirements()).isNull();
    }

    @Test
    void onlyOwnBranchesAndApprovedActiveCreatorsAreAccepted() {
        Brand brand = brand("Hamra");
        Brand other = brand("Elsewhere");
        CampaignRequest ok = complete(brand, creator("APPROVED", "ACTIVE"));

        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), other.branchIds, ok.priceOfferedCents(),
                ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "branchIds");
        assertRefused(brand, new CampaignRequest(ok.name(), ok.creatorId(), List.of(ok.branchIds().get(0), 999_999_999L),
                ok.priceOfferedCents(), ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "branchIds");
        for (long unusable : new long[] {creator("PENDING_APPROVAL", "ACTIVE"), creator("REJECTED", "ACTIVE"),
                creator("APPROVED", "SUSPENDED"), 999_999_999L}) {
            assertRefused(brand, new CampaignRequest(ok.name(), unusable, ok.branchIds(), ok.priceOfferedCents(),
                    ok.startDate(), ok.lastPostingDate(), ok.deliverables(), null), "creatorId");
        }
        // The draft path checks the same things for what it does contain.
        assertThatThrownBy(() -> service.saveDraft(brand.id, new CampaignRequest("Draft", null, other.branchIds, null,
                null, null, null, null))).isInstanceOf(CampaignValidationException.class);
    }

    // TC-F3.1.3, EX-F3.1.2
    @Test
    void aDraftIsPrivateToTheBrandAndTheCreatorIsNotTold() {
        Brand brand = brand("Hamra");
        Brand other = brand("Elsewhere");
        long creator = creator("APPROVED", "ACTIVE");

        CampaignResponse draft = service.saveDraft(brand.id, complete(brand, creator));

        assertThat(draft.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(draft.sentAt()).isNull();
        assertThat(draft.responseDeadline()).isNull();
        assertThat(notificationsOf(creator)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE campaign_id = ?", Integer.class, draft.id()))
                .isZero();
        // No other brand can see, list, change or send it.
        assertThat(service.list(other.id)).isEmpty();
        assertThatThrownBy(() -> service.get(other.id, draft.id())).isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.updateDraft(other.id, draft.id(), complete(other, creator)))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.send(other.id, draft.id(), complete(other, creator)))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThat(service.get(brand.id, draft.id()).status()).isEqualTo(CampaignStatus.DRAFT);
    }

    @Test
    void aDraftNeedsOnlyAName() {
        Brand brand = brand("Hamra");

        CampaignResponse draft = service.saveDraft(brand.id,
                new CampaignRequest("  Weekend brunch ", null, null, null, null, null, null, null));

        assertThat(draft.name()).isEqualTo("Weekend brunch");
        assertThat(draft.creator()).isNull();
        assertThat(draft.branches()).isEmpty();
        assertThat(draft.priceOfferedCents()).isNull();
        assertThat(draft.deliverables()).isEmpty();
        assertThatThrownBy(() -> service.saveDraft(brand.id, new CampaignRequest(" ", null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(CampaignValidationException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("name"));
    }

    @Test
    void aDraftMayHaveAPastStartDateButCannotBeSentWithOne() {
        Brand brand = brand("Hamra");
        CampaignRequest old = with(complete(brand, creator("APPROVED", "ACTIVE")), "Old", 100L, TODAY.minusDays(3),
                TODAY.plusDays(2));

        CampaignResponse draft = service.saveDraft(brand.id, old);

        assertThat(draft.startDate()).isEqualTo(TODAY.minusDays(3));
        assertRefusedSend(brand, draft.id(), old, "startDate");
    }

    // TC-F3.1.4
    @Test
    void aSavedDraftCanBeReopenedEditedAndSentWithTheEditedDetails() {
        Brand brand = brand("Hamra", "Achrafieh");
        long first = creator("APPROVED", "ACTIVE");
        long second = creator("APPROVED", "ACTIVE");
        CampaignResponse draft = service.saveDraft(brand.id, new CampaignRequest("Brunch", first, List.of(brand.branchIds.get(0)),
                10_000L, TODAY.plusDays(1), TODAY.plusDays(9), List.of(new DeliverableRequest("INSTAGRAM", "POST", 1)), "Old"));

        CampaignResponse reopened = service.get(brand.id, draft.id());
        assertThat(reopened.name()).isEqualTo("Brunch");
        assertThat(reopened.priceOfferedCents()).isEqualTo(10_000);

        CampaignResponse edited = service.updateDraft(brand.id, draft.id(), new CampaignRequest("Brunch v2", second,
                brand.branchIds, 30_000L, TODAY.plusDays(2), TODAY.plusDays(12),
                List.of(new DeliverableRequest("instagram", "reel", 2), new DeliverableRequest("INSTAGRAM", "STORY", 3)),
                "Show the dishes"));
        assertThat(edited.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(edited.branches()).hasSize(2);
        assertThat(edited.deliverables()).hasSize(2);
        assertThat(notificationsOf(first)).isEmpty();
        assertThat(notificationsOf(second)).isEmpty();

        CampaignResponse sent = service.send(brand.id, draft.id(), new CampaignRequest("Brunch final", second,
                List.of(brand.branchIds.get(1)), 31_000L, TODAY.plusDays(3), TODAY.plusDays(13),
                List.of(new DeliverableRequest("INSTAGRAM", "CAROUSEL", 1)), "Final words"));

        assertThat(sent.id()).isEqualTo(draft.id());
        assertThat(sent.status()).isEqualTo(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        assertThat(sent.name()).isEqualTo("Brunch final");
        assertThat(sent.priceOfferedCents()).isEqualTo(31_000);
        assertThat(sent.branches()).extracting(b -> b.id()).containsExactly(brand.branchIds.get(1));
        assertThat(sent.deliverables()).extracting(d -> d.type().name()).containsExactly("CAROUSEL");
        assertThat(sent.contentRequirements()).isEqualTo("Final words");
        // Only the creator who is on the sent campaign is told.
        assertThat(notificationsOf(first)).isEmpty();
        assertThat(notificationsOf(second)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_deliverable WHERE campaign_id = ?", Integer.class,
                draft.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_branch WHERE campaign_id = ?", Integer.class,
                draft.id())).isEqualTo(1);
    }

    @Test
    void aRefusedSendLeavesTheDraftAsItWas() {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignResponse draft = service.saveDraft(brand.id, complete(brand, creator));

        assertRefusedSend(brand, draft.id(), with(complete(brand, creator), "Changed", null, null, null), "priceOfferedCents");

        CampaignResponse after = service.get(brand.id, draft.id());
        assertThat(after.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(after.name()).isEqualTo("Summer menu launch");
        assertThat(after.priceOfferedCents()).isEqualTo(25_000);
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    void aSentCampaignCannotBeChangedOrSentAgain() {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest request = complete(brand, creator);
        CampaignResponse sent = service.send(brand.id, request);

        assertThatThrownBy(() -> service.updateDraft(brand.id, sent.id(), request)).isInstanceOf(CampaignNotEditableException.class);
        assertThatThrownBy(() -> service.send(brand.id, sent.id(), request)).isInstanceOf(CampaignNotEditableException.class);

        assertThat(notificationsOf(creator)).hasSize(1);
    }

    @Test
    void twoSendsAtTheSameMomentNotifyTheCreatorOnce() throws Exception {
        Brand brand = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest request = complete(brand, creator);
        CampaignResponse draft = service.saveDraft(brand.id, request);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                go.await();
                try {
                    service.send(brand.id, draft.id(), request);
                    return true;
                } catch (CampaignNotEditableException e) {
                    return false;
                }
            };
            Future<Boolean> a = pool.submit(attempt);
            Future<Boolean> b = pool.submit(attempt);
            go.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(notificationsOf(creator)).hasSize(1);
    }

    // EX-F3.1.3
    @Test
    void aBrandWithNoBranchCannotCreateOrSendACampaign() {
        Brand noBranch = brand();
        Brand other = brand("Hamra");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignRequest request = complete(other, creator);
        CampaignResponse existing = service.saveDraft(other.id, request);

        assertThatThrownBy(() -> service.saveDraft(noBranch.id, new CampaignRequest("Idea", null, null, null, null, null, null, null)))
                .isInstanceOf(NoBranchException.class);
        assertThatThrownBy(() -> service.send(noBranch.id, request)).isInstanceOf(NoBranchException.class);
        assertThatThrownBy(() -> service.updateDraft(noBranch.id, existing.id(), request)).isInstanceOf(NoBranchException.class);
        assertThatThrownBy(() -> service.send(noBranch.id, existing.id(), request)).isInstanceOf(NoBranchException.class);
        assertThat(campaignCount(noBranch.id)).isZero();
    }

    @Test
    void theListShowsOnlyTheBrandsOwnCampaignsWithCreatorAndStatus() {
        Brand brand = brand("Hamra");
        Brand other = brand("Elsewhere");
        long creator = creator("APPROVED", "ACTIVE");
        CampaignResponse draft = service.saveDraft(brand.id, new CampaignRequest("Draft one", null, null, null, null, null, null, null));
        CampaignResponse sent = service.send(brand.id, complete(brand, creator));
        service.saveDraft(other.id, new CampaignRequest("Not mine", null, null, null, null, null, null, null));

        List<CampaignSummaryResponse> list = service.list(brand.id);

        // Newest change first.
        assertThat(list).extracting(CampaignSummaryResponse::id).containsExactly(sent.id(), draft.id());
        assertThat(list.get(0).status()).isEqualTo(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        assertThat(list.get(0).creatorName()).isEqualTo("Creator " + creator);
        assertThat(list.get(1).creatorName()).isNull();
    }

    @Test
    void findCreatorReturnsOnlyCreatorsWhoCanReceiveRequests() {
        long approved = creator("APPROVED", "ACTIVE");

        assertThat(service.findCreator(approved).displayName()).isEqualTo("Creator " + approved);
        assertThatThrownBy(() -> service.findCreator(creator("PENDING_APPROVAL", "ACTIVE")))
                .isInstanceOf(CreatorNotFoundException.class);
        assertThatThrownBy(() -> service.findCreator(999_999_999L)).isInstanceOf(CreatorNotFoundException.class);
    }

    @Test
    void savingAndSendingAreAudited() {
        Brand brand = brand("Hamra");
        CampaignResponse draft = service.saveDraft(brand.id, complete(brand, creator("APPROVED", "ACTIVE")));
        service.updateDraft(brand.id, draft.id(), complete(brand, creator("APPROVED", "ACTIVE")));

        assertThat(audit("CAMPAIGN_DRAFT_SAVED", draft.id())).isEqualTo(1);
        assertThat(audit("CAMPAIGN_DRAFT_UPDATED", draft.id())).isEqualTo(1);
        assertThat(audit("CAMPAIGN_SENT", draft.id())).isZero();
    }

    // ---- helpers ----

    private record Brand(long id, List<Long> branchIds) {
    }

    private void assertRefused(Brand brand, CampaignRequest request, String field) {
        assertThatThrownBy(() -> service.send(brand.id, request))
                .isInstanceOfSatisfying(CampaignValidationException.class,
                        e -> assertThat(e.getErrors()).containsKey(field));
    }

    private void assertRefusedSend(Brand brand, long campaignId, CampaignRequest request, String field) {
        assertThatThrownBy(() -> service.send(brand.id, campaignId, request))
                .isInstanceOfSatisfying(CampaignValidationException.class,
                        e -> assertThat(e.getErrors()).containsKey(field));
    }

    /** A request with every required field filled in, using all of the brand's branches. */
    private CampaignRequest complete(Brand brand, long creatorId) {
        return new CampaignRequest("Summer menu launch", creatorId, brand.branchIds, 25_000L, TODAY.plusDays(1),
                TODAY.plusDays(20), List.of(new DeliverableRequest("INSTAGRAM", "REEL", 2)), null);
    }

    private static CampaignRequest with(CampaignRequest r, String name, Long price, LocalDate start, LocalDate last) {
        return new CampaignRequest(name, r.creatorId(), r.branchIds(), price, start, last, r.deliverables(),
                r.contentRequirements());
    }

    private static CampaignRequest withDeliverables(CampaignRequest r, DeliverableRequest... rows) {
        return new CampaignRequest(r.name(), r.creatorId(), r.branchIds(), r.priceOfferedCents(), r.startDate(),
                r.lastPostingDate(), List.of(rows), r.contentRequirements());
    }

    private Brand brand(String... branchNames) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'BRAND', 'ACTIVE') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Karim Burgers', 'K', '+961 1')", id);
        List<Long> branchIds = new java.util.ArrayList<>();
        for (String name : branchNames) {
            branchIds.add(jdbc.queryForObject("INSERT INTO branch (brand_id, name, location) VALUES (?, ?, 'Beirut') RETURNING id",
                    Long.class, id, name));
        }
        return new Brand(id, branchIds);
    }

    private long creator(String verification, String userStatus) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'CREATOR', ?) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com", userStatus);
        // A rejection must carry a reason (CHECK in V2).
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents,
                                             verification_status, rejection_reason)
                VALUES (?, ?, 'Burgers', 'Beirut', 1000, 5000, ?, CASE WHEN ? = 'REJECTED' THEN 'no' END)
                """, id, "Creator " + id, verification, verification);
        return id;
    }

    private List<Map<String, Object>> notificationsOf(long userId) {
        return jdbc.queryForList("SELECT * FROM notification WHERE user_id = ?", userId);
    }

    private int campaignCount(long brandId) {
        return jdbc.queryForObject("SELECT count(*) FROM campaign WHERE brand_id = ?", Integer.class, brandId);
    }

    private int audit(String action, long campaignId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND entity_type = 'campaign' AND entity_id = ?",
                Integer.class, action, campaignId);
    }
}
