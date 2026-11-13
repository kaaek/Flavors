package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.DeliverableItem;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.Role;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.CampaignResponseService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Responding to a campaign request (UC-F4.1), run against the real database: the answers, the counter-offer,
 * Timer A and the audit rows. The unit test next to it checks the same rules with mocks.
 *
 * <p>The scheduled expiry job may run while these tests do, so the Timer A tests check the final state of
 * their own campaign and never how many campaigns one call expired.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CampaignResponseServiceImplIT {

    private static final long PRICE = 50_000L;

    @Autowired
    CampaignResponseService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository profiles;

    @Autowired
    JdbcTemplate jdbc;

    private long newCreator() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        return id;
    }

    private long newBrand() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.BRAND)).getId();
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Burger Co', 'Karim', '70000000')", id);
        return id;
    }

    /** A request the brand sent: AWAITING_CREATOR_RESPONSE with one Instagram reel, answer due in {@code deadlineInSeconds}. */
    private long newRequest(long brand, long creator, long deadlineInSeconds) {
        Timestamp deadline = Timestamp.from(Instant.now().plusSeconds(deadlineInSeconds));
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents,
                                      start_date, last_posting_date, sent_at, response_deadline)
                VALUES (?, ?, 'Summer burgers', 'AWAITING_CREATOR_RESPONSE', ?,
                        current_date + 1, current_date + 10, now() - interval '1 day', ?)
                RETURNING id
                """, Long.class, brand, creator, PRICE, deadline);
        jdbc.update("INSERT INTO campaign_deliverable (campaign_id, platform, type, count) VALUES (?, 'INSTAGRAM', 'REEL', 1)",
                campaign);
        return campaign;
    }

    private long openRequest(long brand, long creator) {
        return newRequest(brand, creator, 2 * 86_400);
    }

    private String statusOf(long campaign) {
        return jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, campaign);
    }

    private Long agreedPriceOf(long campaign) {
        return jdbc.queryForObject("SELECT agreed_price_cents FROM campaign WHERE id = ?", Long.class, campaign);
    }

    private long auditCount(String action, long campaign) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = ? AND entity_type = 'campaign' AND entity_id = ?",
                Long.class, action, campaign);
    }

    private long notificationCount(long user, long campaign, String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE user_id = ? AND campaign_id = ? AND type = ?",
                Long.class, user, campaign, type);
    }

    // TC-F4.1.1
    @Test
    void acceptingWithinThreeDaysMovesTheCampaignToAwaitingPayment() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        service.acceptCampaign(lara, campaign);

        assertThat(statusOf(campaign)).isEqualTo("AWAITING_PAYMENT");
        assertThat(agreedPriceOf(campaign)).isEqualTo(PRICE);
        assertThat(jdbc.queryForObject("SELECT payment_deadline IS NOT NULL FROM campaign WHERE id = ?", Boolean.class, campaign))
                .isTrue();
        assertThat(auditCount("CAMPAIGN_ACCEPTED", campaign)).isEqualTo(1);
    }

    // TC-F4.1.2
    @Test
    void rejectingMeansTheCampaignDoesNotStart() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        service.rejectCampaign(lara, campaign);

        assertThat(statusOf(campaign)).isEqualTo("REJECTED");
        assertThat(agreedPriceOf(campaign)).isNull();
        assertThat(auditCount("CAMPAIGN_REJECTED", campaign)).isEqualTo(1);
    }

    // TC-F4.1.3
    @Test
    void aDeclinedCounterOfferClosesTheRequestAndTellsTheCreator() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(65_000L, null));

        service.declineCounterOffer(brand, campaign);

        assertThat(statusOf(campaign)).isEqualTo("DECLINED");
        assertThat(agreedPriceOf(campaign)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM counter_offer WHERE campaign_id = ?", String.class, campaign))
                .isEqualTo("DECLINED");
        assertThat(notificationCount(lara, campaign, "COUNTER_OFFER_DECLINED")).isEqualTo(1);
        assertThat(auditCount("COUNTER_OFFER_DECLINED", campaign)).isEqualTo(1);
    }

    // TC-F4.1.4
    @Test
    void anApprovedCounterOfferAwaitsPaymentAtTheNewPriceAndDeliverables() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        service.submitCounterOffer(lara, campaign,
                new CounterOfferRequest(65_000L, List.of(new DeliverableItem("Instagram", "Story", 2))));
        assertThat(statusOf(campaign)).isEqualTo("COUNTERED");
        assertThat(jdbc.queryForObject("SELECT status FROM counter_offer WHERE campaign_id = ?", String.class, campaign))
                .isEqualTo("PENDING");

        service.approveCounterOffer(brand, campaign);

        assertThat(statusOf(campaign)).isEqualTo("AWAITING_PAYMENT");
        assertThat(agreedPriceOf(campaign)).isEqualTo(65_000L);
        List<Map<String, Object>> deliverables = jdbc.queryForList(
                "SELECT platform, type, count FROM campaign_deliverable WHERE campaign_id = ?", campaign);
        assertThat(deliverables).hasSize(1);
        assertThat(deliverables.get(0).get("platform")).isEqualTo("INSTAGRAM");
        assertThat(deliverables.get(0).get("type")).isEqualTo("STORY");
        assertThat(((Number) deliverables.get(0).get("count")).intValue()).isEqualTo(2);
        assertThat(auditCount("COUNTER_OFFER_SUBMITTED", campaign)).isEqualTo(1);
        assertThat(auditCount("COUNTER_OFFER_APPROVED", campaign)).isEqualTo(1);
    }

    // TC-F4.1.5
    @Test
    void anUnansweredRequestExpiresAndTheBrandIsTold() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = newRequest(brand, lara, -60);

        service.expireOverdueCampaignResponses();

        assertThat(statusOf(campaign)).isEqualTo("EXPIRED");
        assertThat(agreedPriceOf(campaign)).isNull();
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isEqualTo(1);
    }

    @Test
    void aRequestStillInsideTheThreeDaysIsNotExpired() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        service.expireOverdueCampaignResponses();

        assertThat(statusOf(campaign)).isEqualTo("AWAITING_CREATOR_RESPONSE");
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isZero();
    }

    // TC-F4.1.6 and EX-F4.1.4
    @Test
    void aSecondCounterOfferIsRefused() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(60_000L, null));

        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(61_000L, null)))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_offer WHERE campaign_id = ?", Long.class, campaign))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT price_cents FROM counter_offer WHERE campaign_id = ?", Long.class, campaign))
                .isEqualTo(60_000L);
    }

    @Test
    void aCounterOfferMustChangeSomething() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(null, null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign,
                new CounterOfferRequest(null, List.of(new DeliverableItem("Tiktok", "Dance", 1)))))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(statusOf(campaign)).isEqualTo("AWAITING_CREATOR_RESPONSE");
    }

    @Test
    void aLateAnswerIsRefused() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = newRequest(brand, lara, -60);

        assertThatThrownBy(() -> service.acceptCampaign(lara, campaign))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(statusOf(campaign)).isNotEqualTo("AWAITING_PAYMENT");
    }

    // NF-1
    @Test
    void anotherCreatorsOrBrandsCampaignIsNotFound() {
        long brand = newBrand();
        long lara = newCreator();
        long otherCreator = newCreator();
        long otherBrand = newBrand();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(60_000L, null));

        assertThatThrownBy(() -> service.rejectCampaign(otherCreator, campaign))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.approveCounterOffer(otherBrand, campaign))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(statusOf(campaign)).isEqualTo("COUNTERED");
    }
}
