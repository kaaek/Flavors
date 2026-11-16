package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.OfferRequest;
import com.flavors.dto.OfferResponse;
import com.flavors.enums.OfferState;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.OfferLockedException;
import com.flavors.exception.OfferValidationException;
import com.flavors.service.CampaignService;
import com.flavors.service.OfferService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-F10.1 against real Postgres: TC-F10.1.1 to TC-F10.1.4, EX-F10.1.1 to EX-F10.1.3, every field rule, who may change
 * the offer and when, and whether it is live (FR-F10.4). Each test makes its own brand, creator and campaign.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OfferServiceImplIT {

    /** The service reads "today" and the expiry midnight in this zone, so the tests do too. */
    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");
    private static final LocalDate TODAY = LocalDate.now(BEIRUT);

    private static final List<DayOfWeek> WEEKDAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    @Autowired
    OfferService service;

    @Autowired
    CampaignService campaigns;

    @Autowired
    JdbcTemplate jdbc;

    // TC-F10.1.1
    @Test
    void a20PercentOfferWithConditionsIsSavedAndLinkedToTheCampaignAndCreator() {
        Brand brand = brand("Hamra", "Achrafieh");
        long creator = creator();
        long campaign = draft(brand, creator);

        OfferResponse saved = service.save(brand.id, campaign, new OfferRequest(20, TODAY.plusDays(30), WEEKDAYS,
                "12:00", "15:30", true, null, 2_000L, 100, 2));

        assertThat(saved.campaignId()).isEqualTo(campaign);
        assertThat(saved.creatorId()).isEqualTo(creator);
        assertThat(saved.discountPercent()).isEqualTo(20);
        assertThat(saved.expiresOn()).isEqualTo(TODAY.plusDays(30));
        // It works through the whole expiry day: it stops at the next midnight in Beirut.
        assertThat(saved.expiresAt()).isEqualTo(TODAY.plusDays(31).atStartOfDay(BEIRUT).toInstant());
        assertThat(saved.validDays()).containsExactlyElementsOf(WEEKDAYS);
        assertThat(saved.validFrom()).isEqualTo("12:00");
        assertThat(saved.validTo()).isEqualTo("15:30");
        assertThat(saved.allBranches()).isTrue();
        assertThat(saved.branchIds()).isEmpty();
        assertThat(saved.minPurchaseCents()).isEqualTo(2_000);
        assertThat(saved.maxRedemptions()).isEqualTo(100);
        assertThat(saved.perPersonLimit()).isEqualTo(2);
        assertThat(saved.redemptions()).isZero();
        assertThat(saved.state()).isEqualTo(OfferState.NOT_LIVE_YET);
        assertThat(saved.editable()).isTrue();

        // The row agrees, including the day bits (Monday to Friday = 1 + 2 + 4 + 8 + 16). JDBC reads smallint as Integer.
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM offer WHERE campaign_id = ?", campaign);
        assertThat(row).containsEntry("id", saved.id()).containsEntry("discount_percent", 20)
                .containsEntry("valid_days", 31).containsEntry("all_branches", true)
                .containsEntry("min_purchase_cents", 2_000L).containsEntry("max_redemptions", 100)
                .containsEntry("per_person_limit", 2);
        // And it reads back the same (the database keeps microseconds, so the save time may differ in the last digits).
        assertThat(service.get(brand.id, campaign)).hasValueSatisfying(read -> assertThat(read)
                .usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(saved));
        assertThat(audit("OFFER_CREATED", campaign)).isEqualTo(1);
    }

    // TC-F10.1.2, EX-F10.1.1
    @Test
    void withoutAPerPersonLimitEachPersonCanUseItOnce() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());

        OfferResponse saved = service.save(brand.id, campaign, minimal(15));

        assertThat(saved.perPersonLimit()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT per_person_limit FROM offer WHERE campaign_id = ?", Short.class, campaign))
                .isEqualTo((short) 1);
        // The other conditions left out get their defaults too: every day, all day, all branches, no minimum, no maximum.
        assertThat(saved.validDays()).containsExactly(DayOfWeek.values());
        assertThat(saved.validFrom()).isNull();
        assertThat(saved.validTo()).isNull();
        assertThat(saved.allBranches()).isTrue();
        assertThat(saved.minPurchaseCents()).isZero();
        assertThat(saved.maxRedemptions()).isNull();
    }

    // TC-F10.1.3, EX-F10.1.2
    @Test
    void selectedBranchesAreSavedAndTheOfferIsLimitedToThem() {
        Brand brand = brand("Hamra", "Achrafieh", "Jounieh");
        long campaign = draft(brand, creator());
        List<Long> chosen = List.of(brand.branchIds.get(0), brand.branchIds.get(2));

        OfferResponse saved = service.save(brand.id, campaign, new OfferRequest(10, TODAY.plusDays(7), null,
                null, null, false, chosen, null, null, null));

        assertThat(saved.allBranches()).isFalse();
        assertThat(saved.branchIds()).containsExactlyInAnyOrderElementsOf(chosen);
        assertThat(jdbc.queryForList("SELECT branch_id FROM offer_branch WHERE offer_id = ?", Long.class, saved.id()))
                .containsExactlyInAnyOrderElementsOf(chosen);
        assertThat(jdbc.queryForObject("SELECT all_branches FROM offer WHERE id = ?", Boolean.class, saved.id())).isFalse();
    }

    // TC-F10.1.4, EX-F10.1.3
    @Test
    void aDiscountOf150PercentIsRefusedAndNothingIsSaved() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());

        assertRefused(brand, campaign, minimal(150), "discountPercent");
        assertRefused(brand, campaign, minimal(0), "discountPercent");
        assertRefused(brand, campaign, minimal(null), "discountPercent");
        assertThat(offerCount(campaign)).isZero();

        // The edges are allowed.
        assertThat(service.save(brand.id, campaign, minimal(1)).discountPercent()).isEqualTo(1);
        assertThat(service.save(brand.id, campaign, minimal(100)).discountPercent()).isEqualTo(100);
    }

    // EX-F10.1.3
    @Test
    void anExpiryInThePastIsRefusedButTodayIsAllowed() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());

        assertRefused(brand, campaign, expiring(TODAY.minusDays(1)), "expiresOn");
        assertRefused(brand, campaign, expiring(null), "expiresOn");
        assertThat(offerCount(campaign)).isZero();

        assertThat(service.save(brand.id, campaign, expiring(TODAY)).expiresOn()).isEqualTo(TODAY);
    }

    @Test
    void everyFieldRuleIsCheckedAndAllWrongFieldsComeBackTogether() {
        Brand brand = brand("Hamra");
        Brand other = brand("Elsewhere");
        long campaign = draft(brand, creator());

        assertRefused(brand, campaign, new OfferRequest(20, TODAY.plusDays(7), List.of(), null, null, null, null, null,
                null, null), "validDays");
        assertRefused(brand, campaign, hours("12:00", null), "validTo");
        assertRefused(brand, campaign, hours(null, "15:00"), "validFrom");
        assertRefused(brand, campaign, hours("15:00", "12:00"), "validTo");
        assertRefused(brand, campaign, hours("12:00", "12:00"), "validTo");
        assertRefused(brand, campaign, hours("noon", "15:00"), "validFrom");
        assertRefused(brand, campaign, branches(List.of()), "branchIds");
        assertRefused(brand, campaign, branches(other.branchIds), "branchIds");
        assertRefused(brand, campaign, new OfferRequest(20, TODAY.plusDays(7), null, null, null, null, null, -1L, null,
                null), "minPurchaseCents");
        assertRefused(brand, campaign, new OfferRequest(20, TODAY.plusDays(7), null, null, null, null, null, null, 0,
                null), "maxRedemptions");
        assertRefused(brand, campaign, new OfferRequest(20, TODAY.plusDays(7), null, null, null, null, null, null, null,
                0), "perPersonLimit");
        assertRefused(brand, campaign, new OfferRequest(20, TODAY.plusDays(7), null, null, null, null, null, null, null,
                101), "perPersonLimit");

        OfferRequest manyWrong = new OfferRequest(150, TODAY.minusDays(1), List.of(), "12:00", null, false, List.of(),
                -1L, 0, 0);
        assertThatThrownBy(() -> service.save(brand.id, campaign, manyWrong))
                .isInstanceOfSatisfying(OfferValidationException.class, e -> assertThat(e.getErrors()).containsOnlyKeys(
                        "discountPercent", "expiresOn", "validDays", "validTo", "branchIds", "minPurchaseCents",
                        "maxRedemptions", "perPersonLimit"));
        assertThat(offerCount(campaign)).isZero();
    }

    @Test
    void savingAgainReplacesEveryConditionOfTheSameOffer() {
        Brand brand = brand("Hamra", "Achrafieh");
        long campaign = draft(brand, creator());
        OfferResponse first = service.save(brand.id, campaign, new OfferRequest(20, TODAY.plusDays(7), WEEKDAYS,
                "12:00", "15:00", false, List.of(brand.branchIds.get(0)), 1_000L, 50, 3));

        OfferResponse second = service.save(brand.id, campaign, minimal(25));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.discountPercent()).isEqualTo(25);
        assertThat(second.validDays()).containsExactly(DayOfWeek.values());
        assertThat(second.validFrom()).isNull();
        assertThat(second.allBranches()).isTrue();
        assertThat(second.perPersonLimit()).isEqualTo(1);
        assertThat(offerCount(campaign)).isEqualTo(1);
        // Back to all branches: the old selection is gone.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offer_branch WHERE offer_id = ?", Integer.class, first.id()))
                .isZero();
        assertThat(audit("OFFER_CREATED", campaign)).isEqualTo(1);
        assertThat(audit("OFFER_UPDATED", campaign)).isEqualTo(1);
    }

    @Test
    void theOfferCanBeChangedUntilTheCampaignIsActiveAndIsLockedAfter() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());
        service.save(brand.id, campaign, minimal(20));

        // Sent, then waiting for payment: still the brand's to change.
        campaigns.send(brand.id, campaign, complete(brand, creatorOf(campaign)));
        assertThat(service.save(brand.id, campaign, minimal(21)).editable()).isTrue();
        setStatus(campaign, "AWAITING_PAYMENT");
        assertThat(service.save(brand.id, campaign, minimal(22)).discountPercent()).isEqualTo(22);

        // Paid and active: diners may hold QR codes, so it is locked.
        setStatus(campaign, "ACTIVE");
        assertThatThrownBy(() -> service.save(brand.id, campaign, minimal(50))).isInstanceOf(OfferLockedException.class);
        assertThat(service.get(brand.id, campaign)).hasValueSatisfying(offer -> {
            assertThat(offer.discountPercent()).isEqualTo(22);
            assertThat(offer.editable()).isFalse();
        });
    }

    @Test
    void aCampaignThatEndedWithoutGoingActiveCantGetAnOffer() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());
        campaigns.send(brand.id, campaign, complete(brand, creatorOf(campaign)));
        setStatus(campaign, "REJECTED");

        assertThatThrownBy(() -> service.save(brand.id, campaign, minimal(20))).isInstanceOf(OfferLockedException.class);
        assertThat(offerCount(campaign)).isZero();
    }

    @Test
    void anotherBrandsCampaignIsNotFound() {
        Brand brand = brand("Hamra");
        Brand other = brand("Elsewhere");
        long campaign = draft(brand, creator());
        service.save(brand.id, campaign, minimal(20));

        assertThatThrownBy(() -> service.get(other.id, campaign)).isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.save(other.id, campaign, minimal(90)))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThat(service.get(brand.id, campaign)).hasValueSatisfying(o -> assertThat(o.discountPercent()).isEqualTo(20));
    }

    @Test
    void aCampaignWithoutAnOfferHasNone() {
        Brand brand = brand("Hamra");
        long campaign = draft(brand, creator());

        assertThat(service.get(brand.id, campaign)).isEmpty();
    }

    // FR-F10.4
    @Test
    void theOfferIsLiveOnlyWhileTheCampaignRunsBeforeTheExpiryAndBelowTheMaximum() {
        Brand brand = brand("Hamra");
        long creator = creator();
        long campaign = draft(brand, creator);
        service.save(brand.id, campaign, new OfferRequest(20, TODAY.plusDays(7), null, null, null, null, null, null, 2,
                null));
        campaigns.send(brand.id, campaign, complete(brand, creator));
        assertThat(state(brand, campaign)).isEqualTo(OfferState.NOT_LIVE_YET);

        setStatus(campaign, "ACTIVE");
        assertThat(state(brand, campaign)).isEqualTo(OfferState.LIVE);
        setStatus(campaign, "PROOF_SUBMITTED");
        assertThat(state(brand, campaign)).isEqualTo(OfferState.LIVE);

        // Its maximum of 2 redemptions is reached.
        redeem(brand, creator, campaign);
        assertThat(service.get(brand.id, campaign)).hasValueSatisfying(o -> {
            assertThat(o.redemptions()).isEqualTo(1);
            assertThat(o.state()).isEqualTo(OfferState.LIVE);
        });
        redeem(brand, creator, campaign);
        assertThat(state(brand, campaign)).isEqualTo(OfferState.FULL);

        setStatus(campaign, "COMPLETED");
        assertThat(state(brand, campaign)).isEqualTo(OfferState.ENDED);
    }

    // FR-F10.4
    @Test
    void anActiveOfferStopsAtItsExpiry() {
        Brand brand = brand("Hamra");
        long creator = creator();
        long campaign = draft(brand, creator);
        service.save(brand.id, campaign, minimal(20));
        campaigns.send(brand.id, campaign, complete(brand, creator));
        setStatus(campaign, "ACTIVE");

        jdbc.update("UPDATE offer SET expires_at = now() - interval '1 minute' WHERE campaign_id = ?", campaign);

        assertThat(state(brand, campaign)).isEqualTo(OfferState.EXPIRED);
    }

    // ---- Helpers ----

    private record Brand(long id, List<Long> branchIds) {
    }

    private void assertRefused(Brand brand, long campaign, OfferRequest request, String field) {
        assertThatThrownBy(() -> service.save(brand.id, campaign, request))
                .isInstanceOfSatisfying(OfferValidationException.class,
                        e -> assertThat(e.getErrors()).containsKey(field));
    }

    /** A discount and an expiry, the two fields without a default. */
    private static OfferRequest minimal(Integer discount) {
        return new OfferRequest(discount, TODAY.plusDays(7), null, null, null, null, null, null, null, null);
    }

    private static OfferRequest expiring(LocalDate expiresOn) {
        return new OfferRequest(20, expiresOn, null, null, null, null, null, null, null, null);
    }

    private static OfferRequest hours(String from, String to) {
        return new OfferRequest(20, TODAY.plusDays(7), null, from, to, null, null, null, null, null);
    }

    private static OfferRequest branches(List<Long> branchIds) {
        return new OfferRequest(20, TODAY.plusDays(7), null, null, null, false, branchIds, null, null, null);
    }

    private OfferState state(Brand brand, long campaign) {
        return service.get(brand.id, campaign).orElseThrow().state();
    }

    /** A draft with a creator, so the offer has a creator to be linked to. */
    private long draft(Brand brand, long creator) {
        return campaigns.saveDraft(brand.id, complete(brand, creator)).id();
    }

    private static CampaignRequest complete(Brand brand, long creator) {
        return new CampaignRequest("Summer menu launch", creator, brand.branchIds, 25_000L, TODAY.plusDays(1),
                TODAY.plusDays(20), List.of(new CampaignRequest.DeliverableRequest("INSTAGRAM", "REEL", 2)), null);
    }

    private long creatorOf(long campaign) {
        return jdbc.queryForObject("SELECT creator_id FROM campaign WHERE id = ?", Long.class, campaign);
    }

    /** Moves the campaign on directly: the stories that do it in the app (answer, payment, proof) are tested elsewhere. */
    private void setStatus(long campaign, String status) {
        jdbc.update("UPDATE campaign SET status = ? WHERE id = ?", status, campaign);
    }

    /** One diner's claim, scanned at the till: the rows the redemption story (UC-F11.1) will write. */
    private void redeem(Brand brand, long creator, long campaign) {
        long offer = jdbc.queryForObject("SELECT id FROM offer WHERE campaign_id = ?", Long.class, campaign);
        long claim = jdbc.queryForObject("""
                INSERT INTO offer_claim (offer_id, phone_enc, phone_hash, consent_at, qr_token, status)
                VALUES (?, decode('00', 'hex'), ?, now(), ?, 'REDEEMED') RETURNING id
                """, Long.class, offer, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO redemption (claim_id, offer_id, campaign_id, creator_id, brand_id, branch_id, bill_cents,
                                        discount_cents)
                VALUES (?, ?, ?, ?, ?, ?, 5000, 1000)
                """, claim, offer, campaign, creator, brand.id, brand.branchIds.get(0));
    }

    private int offerCount(long campaign) {
        return jdbc.queryForObject("SELECT count(*) FROM offer WHERE campaign_id = ?", Integer.class, campaign);
    }

    private int audit(String action, long campaign) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ? AND entity_type = 'campaign' "
                + "AND entity_id = ?", Integer.class, action, campaign);
    }

    private Brand brand(String... branchNames) {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'BRAND', 'ACTIVE') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Karim Burgers', 'K', "
                + "'+961 1')", id);
        List<Long> branchIds = new ArrayList<>();
        for (String name : branchNames) {
            branchIds.add(jdbc.queryForObject("INSERT INTO branch (brand_id, name, location) VALUES (?, ?, 'Beirut') "
                    + "RETURNING id", Long.class, id, name));
        }
        return new Brand(id, branchIds);
    }

    /** An approved, active creator: the only kind a campaign can be for. */
    private long creator() {
        long id = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, role, status) VALUES (?, 'hash', 'CREATOR', 'ACTIVE') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        jdbc.update("""
                INSERT INTO creator_profile (user_id, display_name, food_category, location, price_min_cents, price_max_cents,
                                             verification_status)
                VALUES (?, ?, 'Burgers', 'Beirut', 1000, 5000, 'APPROVED')
                """, id, "Creator " + id);
        return id;
    }
}
