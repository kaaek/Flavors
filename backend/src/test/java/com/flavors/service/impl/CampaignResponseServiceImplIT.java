package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.CounterOfferRequest;
import com.flavors.dto.DeliverableItem;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignResponseStateException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.CampaignResponseService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Responding to a campaign request (UC-F4.1), run against the real database: the answers, the counter-offer,
 * Timer A, the audit rows, and what happens when two calls hit the same campaign at once.
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

    private void makeOverdue(long campaign) {
        jdbc.update("UPDATE campaign SET response_deadline = now() - interval '1 minute' WHERE id = ?", campaign);
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
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_ACCEPTED")).isEqualTo(1);
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
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_REJECTED")).isEqualTo(1);
    }

    // TC-F4.1.3
    @Test
    void aDeclinedCounterOfferClosesTheRequestAndTellsTheCreator() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(65_000L, null));
        assertThat(notificationCount(brand, campaign, "COUNTER_OFFER_RECEIVED")).isEqualTo(1);

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
        assertThat(notificationCount(lara, campaign, "COUNTER_OFFER_APPROVED")).isEqualTo(1);
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
        assertThat(notificationCount(lara, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isZero();
    }

    // The 3 days cover the whole negotiation: a counter nobody decides on expires too.
    @Test
    void aCounteredRequestNobodyDecidesOnExpiresAndBothSidesAreTold() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(65_000L, null));
        makeOverdue(campaign);

        service.expireOverdueCampaignResponses();

        assertThat(statusOf(campaign)).isEqualTo("EXPIRED");
        assertThat(agreedPriceOf(campaign)).isNull();
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isEqualTo(1);
        assertThat(notificationCount(lara, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isEqualTo(1);
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

    // TC-F4.1.6 and EX-F4.1.4: the second counter finds the request already countered.
    @Test
    void aSecondCounterOfferIsRefused() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(60_000L, null));

        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(61_000L, null)))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_offer WHERE campaign_id = ?", Long.class, campaign))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT price_cents FROM counter_offer WHERE campaign_id = ?", Long.class, campaign))
                .isEqualTo(60_000L);
    }

    @Test
    void aCounterOfferMustChangeSomethingAndTheNewPriceMustBePositive() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);

        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(null, null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(0L, null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(PRICE, null)))
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
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(statusOf(campaign)).isNotEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void theBrandCannotDecideOnACounterOfferAfterTheDeadline() {
        long brand = newBrand();
        long lara = newCreator();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(65_000L, null));
        makeOverdue(campaign);

        assertThatThrownBy(() -> service.approveCounterOffer(brand, campaign))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(statusOf(campaign)).isNotEqualTo("AWAITING_PAYMENT");
        assertThat(agreedPriceOf(campaign)).isNull();
    }

    // NF-1: someone else's campaign, and a draft that names her, look exactly like a campaign that does not exist.
    @Test
    void anotherCreatorsOrBrandsCampaignAndADraftAreNotFound() {
        long brand = newBrand();
        long lara = newCreator();
        long otherCreator = newCreator();
        long otherBrand = newBrand();
        long campaign = openRequest(brand, lara);
        service.submitCounterOffer(lara, campaign, new CounterOfferRequest(60_000L, null));
        long draft = openRequest(brand, lara);
        jdbc.update("UPDATE campaign SET status = 'DRAFT' WHERE id = ?", draft);

        assertThatThrownBy(() -> service.rejectCampaign(otherCreator, campaign))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.approveCounterOffer(otherBrand, campaign))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> service.acceptCampaign(lara, draft))
                .isInstanceOf(CampaignNotFoundException.class);

        assertThat(statusOf(campaign)).isEqualTo("COUNTERED");
    }

    // ---- two calls on the same campaign at once: exactly one wins, and the final state agrees with it ----

    /** Runs the call when the start signal comes; true if it went through, false if the campaign refused it. */
    private static Callable<Boolean> attempt(CountDownLatch start, Runnable call) {
        return () -> {
            start.await();
            try {
                call.run();
                return true;
            } catch (CampaignResponseStateException refused) {
                return false;
            }
        };
    }

    @Test
    void acceptAndRejectAtTheSameTimeHaveOneWinner() throws Exception {
        long brand = newBrand();
        long lara = newCreator();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                long campaign = openRequest(brand, lara);
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> accept = pool.submit(attempt(start, () -> service.acceptCampaign(lara, campaign)));
                Future<Boolean> reject = pool.submit(attempt(start, () -> service.rejectCampaign(lara, campaign)));
                start.countDown();

                boolean accepted = accept.get(30, TimeUnit.SECONDS);
                boolean rejected = reject.get(30, TimeUnit.SECONDS);

                assertThat(accepted ^ rejected).as("exactly one of accept and reject goes through").isTrue();
                assertThat(statusOf(campaign)).isEqualTo(accepted ? "AWAITING_PAYMENT" : "REJECTED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void approveAndDeclineAtTheSameTimeHaveOneWinner() throws Exception {
        long brand = newBrand();
        long lara = newCreator();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                long campaign = openRequest(brand, lara);
                service.submitCounterOffer(lara, campaign, new CounterOfferRequest(65_000L, null));
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> approve = pool.submit(attempt(start, () -> service.approveCounterOffer(brand, campaign)));
                Future<Boolean> decline = pool.submit(attempt(start, () -> service.declineCounterOffer(brand, campaign)));
                start.countDown();

                boolean approved = approve.get(30, TimeUnit.SECONDS);
                boolean declined = decline.get(30, TimeUnit.SECONDS);

                assertThat(approved ^ declined).as("exactly one of approve and decline goes through").isTrue();
                assertThat(statusOf(campaign)).isEqualTo(approved ? "AWAITING_PAYMENT" : "DECLINED");
                // The creator is told about the decision that really happened, and only that one.
                assertThat(notificationCount(lara, campaign, "COUNTER_OFFER_DECLINED")).isEqualTo(declined ? 1 : 0);
                assertThat(notificationCount(lara, campaign, "COUNTER_OFFER_APPROVED")).isEqualTo(approved ? 1 : 0);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoCountersAtTheSameTimeHaveOneWinner() throws Exception {
        long brand = newBrand();
        long lara = newCreator();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                long campaign = openRequest(brand, lara);
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> first = pool.submit(attempt(start,
                        () -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(60_000L, null))));
                Future<Boolean> second = pool.submit(attempt(start,
                        () -> service.submitCounterOffer(lara, campaign, new CounterOfferRequest(61_000L, null))));
                start.countDown();

                assertThat(first.get(30, TimeUnit.SECONDS) ^ second.get(30, TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_offer WHERE campaign_id = ?", Long.class, campaign))
                        .isEqualTo(1);
                assertThat(statusOf(campaign)).isEqualTo("COUNTERED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoExpiryRunsAtTheSameTimeTellTheBrandOnlyOnce() throws Exception {
        long brand = newBrand();
        long lara = newCreator();
        List<Long> campaigns = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            campaigns.add(newRequest(brand, lara, -60));
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(attempt(start, service::expireOverdueCampaignResponses));
            Future<Boolean> second = pool.submit(attempt(start, service::expireOverdueCampaignResponses));
            start.countDown();
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);

            for (long campaign : campaigns) {
                assertThat(statusOf(campaign)).isEqualTo("EXPIRED");
                assertThat(notificationCount(brand, campaign, "CAMPAIGN_RESPONSE_EXPIRED")).isEqualTo(1);
                assertThat(auditCount("CAMPAIGN_RESPONSE_EXPIRED", campaign)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
