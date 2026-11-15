package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.PaymentStartResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.enums.Role;
import com.flavors.exception.CampaignResponseStateException;
import com.flavors.exception.PaymentAlreadyPaidException;
import com.flavors.exception.PaymentProviderException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A Pay click on an attempt that may already have been paid at the provider (the payer closed the tab before the return
 * or the callback was processed), against the real database. The provider's answer decides: paid is held and the click is
 * refused as already paid, still waiting reopens the same checkout, ended opens a fresh attempt, and a provider error
 * opens nothing, so a second charge is impossible.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentRestartIT {

    private static final long AGREED = 65_000L;

    @Autowired PaymentService payments;
    @MockitoSpyBean SimulatedPaymentProvider simulated;
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

    private String statusOf(long campaign) {
        return jdbc.queryForObject("SELECT status FROM campaign WHERE id = ?", String.class, campaign);
    }

    private String escrowStatusOf(long campaign) {
        return jdbc.queryForObject("SELECT status FROM escrow_payment WHERE campaign_id = ?", String.class, campaign);
    }

    private long countTransactions(long campaign, String status) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ? AND t.status = ?
                """, Long.class, campaign, status);
    }

    private long countTransactions(long campaign) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ?
                """, Long.class, campaign);
    }

    private String latestReference(long campaign) {
        return jdbc.queryForObject("""
                SELECT t.provider_ref FROM payment_transaction t JOIN escrow_payment e ON t.escrow_id = e.id
                WHERE e.campaign_id = ? ORDER BY t.id DESC LIMIT 1
                """, String.class, campaign);
    }

    private PaymentStartResponse pay(long brand, long campaign) {
        return payments.startPayment(brand, campaign, UUID.randomUUID().toString());
    }

    // Paid at the provider, tab closed, Pay clicked again: held through the normal path, no second checkout.
    @Test
    void payingAgainAfterPayingAtTheProviderIsRefusedAsAlreadyPaidAndTheMoneyIsHeld() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        pay(brand, campaign);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);

        assertThatThrownBy(() -> pay(brand, campaign)).isInstanceOf(PaymentAlreadyPaidException.class);

        assertThat(statusOf(campaign)).isEqualTo("ACTIVE");
        assertThat(escrowStatusOf(campaign)).isEqualTo("HELD");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
        assertThat(countTransactions(campaign)).isEqualTo(1);
        verify(simulated, times(1)).startHostedPayment(any());
    }

    @Test
    void replayingTheKeyAfterPayingAtTheProviderIsAlsoRefusedAsAlreadyPaid() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        String key = UUID.randomUUID().toString();
        payments.startPayment(brand, campaign, key);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);

        assertThatThrownBy(() -> payments.startPayment(brand, campaign, key))
                .isInstanceOf(PaymentAlreadyPaidException.class);

        assertThat(escrowStatusOf(campaign)).isEqualTo("HELD");
        assertThat(countTransactions(campaign)).isEqualTo(1);
    }

    @Test
    void anAttemptStillWaitingAtTheProviderReopensTheSameCheckout() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        PaymentStartResponse first = pay(brand, campaign);

        PaymentStartResponse again = pay(brand, campaign);

        assertThat(again.checkoutUrl()).isEqualTo(first.checkoutUrl());
        assertThat(countTransactions(campaign)).isEqualTo(1);
        assertThat(statusOf(campaign)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void anAttemptTheProviderEndedWithoutPaymentOpensAFreshOne() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        PaymentStartResponse first = pay(brand, campaign);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.FAILED);

        PaymentStartResponse fresh = pay(brand, campaign);

        assertThat(fresh.checkoutUrl()).isNotEqualTo(first.checkoutUrl());
        assertThat(countTransactions(campaign, "FAILED")).isEqualTo(1);
        assertThat(countTransactions(campaign, "PENDING")).isEqualTo(1);
        assertThat(statusOf(campaign)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void aProviderErrorRefusesTheClickAndOpensNothing() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        pay(brand, campaign);
        doThrow(new PaymentProviderException("down")).when(simulated).queryStatus(anyString(), anyLong(), anyString());

        assertThatThrownBy(() -> pay(brand, campaign)).isInstanceOf(PaymentProviderException.class);

        assertThat(countTransactions(campaign)).isEqualTo(1);
        assertThat(countTransactions(campaign, "PENDING")).isEqualTo(1);
        verify(simulated, times(1)).startHostedPayment(any());
    }

    // Paid at the provider, but only after the window closed: recorded, never reported as held.
    @Test
    void aPaymentFoundAfterTheWindowClosedIsNotReportedAsHeld() {
        long brand = newBrand();
        long campaign = newAcceptedCampaign(brand, newCreator());
        String key = UUID.randomUUID().toString();
        payments.startPayment(brand, campaign, key);
        simulated.setOutcome(latestReference(campaign), ProviderPaymentStatus.PAID);
        jdbc.update("UPDATE campaign SET payment_deadline = now() - interval '1 minute' WHERE id = ?", campaign);

        assertThatThrownBy(() -> payments.startPayment(brand, campaign, key))
                .isInstanceOf(CampaignResponseStateException.class);

        assertThat(escrowStatusOf(campaign)).isEqualTo("PENDING");
        assertThat(countTransactions(campaign, "SUCCEEDED")).isEqualTo(1);
    }
}
