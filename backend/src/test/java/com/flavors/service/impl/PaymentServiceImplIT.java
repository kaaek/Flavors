package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.EscrowResponse;
import com.flavors.dto.PaymentResult;
import com.flavors.dto.PaymentStartResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignResponseStateException;
import com.flavors.exception.PaymentNotFoundException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.CampaignService;
import com.flavors.service.PaymentService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
 * Paying for an accepted campaign and holding the money (UC-F7.1), against the real database and the mock
 * provider. Covers TC-F7.1.1 to TC-F7.1.5 and EX-F7.1.1 to EX-F7.1.3.
 *
 * <p>The scheduled jobs may run while these tests do, so each test checks the final state of its own campaign.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentServiceImplIT {

    private static final long AGREED = 65_000L;

    @Autowired PaymentService payments;
    @Autowired CampaignService campaigns;
    @Autowired SimulatedPaymentProvider simulated;
    @Autowired AppUserRepository users;
    @Autowired CreatorProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;

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

    /** A campaign the creator accepted: AWAITING_PAYMENT, agreed at AGREED, accepted one day ago, 2 days left to pay. */
    private long newAcceptedCampaign(long brand, long creator) {
        long campaign = jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                                      start_date, last_posting_date, sent_at, accepted_at, payment_deadline)
                VALUES (?, ?, 'Summer burgers', 'AWAITING_PAYMENT', 50000, ?,
                        current_date + 1, current_date + 10, now() - interval '2 days',
                        now() - interval '1 day', now() + interval '2 days')
                RETURNING id
                """, Long.class, brand, creator, AGREED);
        jdbc.update("INSERT INTO campaign_deliverable (campaign_id, platform, type, count) VALUES (?, 'INSTAGRAM', 'REEL', 1)",
                campaign);
        return campaign;
    }

    private void makeOverdue(long campaign) {
        jdbc.update("UPDATE campaign SET payment_deadline = now() - interval '1 minute' WHERE id = ?", campaign);
    }

    private String statusOf(long campaign) {
        return jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, campaign);
    }

    private String escrowStatusOf(long campaign) {
        return jdbc.queryForList("SELECT status FROM escrow_payment WHERE campaign_id = ?", String.class, campaign)
                .stream().findFirst().orElse(null);
    }

    private long countTransactions(long campaign, String status) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ? AND t.status = ?
                """, Long.class, campaign, status);
    }

    private String latestReference(long campaign) {
        return jdbc.queryForObject("""
                SELECT t.provider_ref FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ? ORDER BY t.id DESC LIMIT 1
                """, String.class, campaign);
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

    private PaymentStartResponse pay(long brand, long campaign) {
        return payments.startPayment(brand, campaign, UUID.randomUUID().toString());
    }

    // TC-F7.1.1
    @Test
    void aPaidCampaignIsHeldAndBecomesActive() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);

        PaymentStartResponse start = pay(brand, campaign);
        assertThat(start.checkoutUrl()).contains(latestReference(campaign));
        assertThat(start.amountCents()).isEqualTo(AGREED);
        assertThat(start.currency()).isEqualTo("USD");
        assertThat(escrowStatusOf(campaign)).isEqualTo("PENDING");

        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);
        payments.reconcile(latestReference(campaign));

        assertThat(escrowStatusOf(campaign)).isEqualTo("HELD");
        assertThat(statusOf(campaign)).isEqualTo("ACTIVE");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(auditCount("ESCROW_HELD", campaign)).isEqualTo(1);
        assertThat(notificationCount(brand, campaign, "ESCROW_HELD")).isEqualTo(1);
        assertThat(notificationCount(creator, campaign, "ESCROW_HELD")).isEqualTo(1);

        EscrowResponse forBrand = payments.getEscrowForBrand(brand, campaign);
        EscrowResponse forCreator = payments.getEscrowForCreator(creator, campaign);
        assertThat(forBrand.status()).isEqualTo(EscrowStatus.HELD);
        assertThat(forBrand.heldAt()).isNotNull();
        assertThat(forCreator.status()).isEqualTo(EscrowStatus.HELD);
        assertThat(forCreator.amountCents()).isEqualTo(AGREED);
    }

    // TC-F7.1.2 and EX-F7.1.1: a declined card keeps the campaign waiting; a retry pays once.
    @Test
    void aDeclinedCardKeepsTheCampaignAwaitingPaymentAndRetryPaysOnce() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);

        pay(brand, campaign);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.FAILED);
        payments.reconcile(latestReference(campaign));

        assertThat(statusOf(campaign)).isEqualTo("AWAITING_PAYMENT");
        assertThat(escrowStatusOf(campaign)).isEqualTo("PENDING");
        assertThat(countTransactions(campaign, "FAILED")).isEqualTo(1);

        // A new Pay click (a new key) starts a new attempt, not a repeat of the failed one.
        pay(brand, campaign);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);
        payments.reconcile(latestReference(campaign));

        assertThat(statusOf(campaign)).isEqualTo("ACTIVE");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(countTransactions(campaign, "FAILED")).isEqualTo(1);
    }

    // NF-6 and FR-F7.8: the same Idempotency-Key is the same request, and a second click reuses the open attempt.
    @Test
    void retriesNeverOpenASecondCharge() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        String key = UUID.randomUUID().toString();

        PaymentStartResponse first = payments.startPayment(brand, campaign, key);
        PaymentStartResponse retry = payments.startPayment(brand, campaign, key);
        PaymentStartResponse otherClick = payments.startPayment(brand, campaign, UUID.randomUUID().toString());

        assertThat(retry.checkoutUrl()).isEqualTo(first.checkoutUrl());
        assertThat(otherClick.checkoutUrl()).isEqualTo(first.checkoutUrl());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ?
                """, Long.class, campaign)).isEqualTo(1);
    }

    @Test
    void theSameKeyForAnotherCampaignIsRefused() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        long other = newAcceptedCampaign(brand, creator);
        String key = UUID.randomUUID().toString();

        payments.startPayment(brand, campaign, key);

        assertThatThrownBy(() -> payments.startPayment(brand, other, key))
                .isInstanceOf(com.flavors.exception.IdempotencyConflictException.class);
    }

    // TC-F7.1.3 and EX-F7.1.2: Timer B expires the campaign and nothing is charged.
    @Test
    void theBrandNotPayingInTimeExpiresTheCampaignAndNobodyIsCharged() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        makeOverdue(campaign);

        payments.expireOverduePayments();

        assertThat(statusOf(campaign)).isEqualTo("EXPIRED");
        assertThat(escrowStatusOf(campaign)).isEqualTo("PENDING");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isZero();
        assertThat(countTransactions(campaign, "PENDING")).isZero();
        assertThat(auditCount("PAYMENT_WINDOW_EXPIRED", campaign)).isEqualTo(1);
        assertThat(notificationCount(brand, campaign, "CAMPAIGN_PAYMENT_EXPIRED")).isEqualTo(1);
        assertThat(notificationCount(creator, campaign, "CAMPAIGN_PAYMENT_EXPIRED")).isEqualTo(1);
    }

    // A payment made before the deadline but confirmed only by the sweep (after the clock passed it) is held.
    @Test
    void aPaymentMadeInTimeWinsOverTimerBEvenWhenItIsConfirmedLater() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);
        makeOverdue(campaign);

        payments.expireOverduePayments();

        assertThat(statusOf(campaign)).isEqualTo("ACTIVE");
        assertThat(escrowStatusOf(campaign)).isEqualTo("HELD");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(auditCount("PAYMENT_WINDOW_EXPIRED", campaign)).isZero();
    }

    @Test
    void aPaymentAfterTheWindowIsRecordedButNotHeld() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        String reference = latestReference(campaign);
        makeOverdue(campaign);
        payments.expireOverduePayments();

        simulated.setOutcome(reference, ProviderPaymentStatus.PAID);
        PaymentResult result = payments.reconcile(reference).orElseThrow();

        assertThat(result.held()).isFalse();
        assertThat(statusOf(campaign)).isEqualTo("EXPIRED");
        assertThat(escrowStatusOf(campaign)).isEqualTo("PENDING");
        assertThat(auditCount("PAYMENT_NOT_HELD", campaign)).isEqualTo(1);
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
    }

    // The idempotent replay of a key after expiry is refused, not sent to the provider again.
    @Test
    void aReplayedKeyAfterExpiryIsRefused() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        String key = UUID.randomUUID().toString();
        payments.startPayment(brand, campaign, key);
        makeOverdue(campaign);
        payments.expireOverduePayments();

        assertThatThrownBy(() -> payments.startPayment(brand, campaign, key))
                .isInstanceOf(CampaignResponseStateException.class);
    }

    @Test
    void aNewPaymentAfterTheWindowIsRefused() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        makeOverdue(campaign);

        assertThatThrownBy(() -> pay(brand, campaign)).isInstanceOf(CampaignResponseStateException.class);
    }

    // TC-F7.1.4 and EX-F7.1.3: a confirmation received twice is recorded once.
    @Test
    void aConfirmationReceivedTwiceIsRecordedOnce() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        String reference = latestReference(campaign);
        simulated.setOutcome(reference, ProviderPaymentStatus.PAID);

        payments.reconcile(reference);
        payments.reconcile(reference);

        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(auditCount("ESCROW_HELD", campaign)).isEqualTo(1);
        assertThat(notificationCount(brand, campaign, "ESCROW_HELD")).isEqualTo(1);
    }

    @Test
    void simultaneousConfirmationsRecordOnePayment() throws Exception {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        String reference = latestReference(campaign);
        simulated.setOutcome(reference, ProviderPaymentStatus.PAID);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(pool.submit(() -> {
                    go.await();
                    return payments.reconcile(reference);
                }));
            }
            go.countDown();
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(auditCount("ESCROW_HELD", campaign)).isEqualTo(1);
        assertThat(notificationCount(creator, campaign, "ESCROW_HELD")).isEqualTo(1);
    }

    // The payment and Timer B race for the same campaign: whichever wins, the end state is consistent.
    @Test
    void aPaymentAndTimerBAtTheSameMomentLeaveAConsistentCampaign() throws Exception {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        String reference = latestReference(campaign);
        simulated.setOutcome(reference, ProviderPaymentStatus.PAID);
        makeOverdue(campaign);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> expiry = pool.submit(() -> {
                go.await();
                return payments.expireOverduePayments();
            });
            Future<?> confirmation = pool.submit(() -> {
                go.await();
                return payments.reconcile(reference);
            });
            go.countDown();
            expiry.get(30, TimeUnit.SECONDS);
            confirmation.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        String status = statusOf(campaign);
        String escrow = escrowStatusOf(campaign);
        if ("ACTIVE".equals(status)) {
            assertThat(escrow).isEqualTo("HELD");
        } else {
            assertThat(status).isEqualTo("EXPIRED");
            assertThat(escrow).isEqualTo("PENDING");
        }
    }

    // The reconciliation sweep picks up a payment whose callback never came.
    @Test
    void theSweepChecksPaymentsStillPending() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        pay(brand, campaign);
        String reference = latestReference(campaign);
        simulated.setOutcome(reference, ProviderPaymentStatus.PAID);

        payments.reconcileOpenPayments(Duration.ZERO);

        assertThat(statusOf(campaign)).isEqualTo("ACTIVE");
        assertThat(escrowStatusOf(campaign)).isEqualTo("HELD");
    }

    // TC-F7.1.5: on day 1 the brand and the creator see Awaiting payment and the deadline, and no escrow yet.
    @Test
    void onDayOneTheCampaignShowsAwaitingPaymentWithTheDeadline() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);

        var response = campaigns.get(brand, campaign);

        assertThat(response.status().name()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.paymentDeadline()).isAfter(java.time.Instant.now().plus(Duration.ofDays(1)));
        assertThat(response.escrowStatus()).isNull();
        assertThat(campaigns.getForCreator(creator, campaign).paymentDeadline()).isNotNull();
    }

    @Test
    void theEscrowIsNotVisibleBeforeAPaymentStarts() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);

        assertThatThrownBy(() -> payments.getEscrowForBrand(brand, campaign))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void anotherBrandCannotPayOrSeeTheEscrow() {
        long brand = newBrand();
        long creator = newCreator();
        long campaign = newAcceptedCampaign(brand, creator);
        long stranger = newBrand();

        assertThatThrownBy(() -> pay(stranger, campaign)).isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> payments.getEscrowForBrand(stranger, campaign))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> payments.getEscrowForCreator(newCreator(), campaign))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void anUnknownPaymentReferenceIsIgnored() {
        assertThat(payments.reconcile(UUID.randomUUID().toString())).isEmpty();
    }
}
