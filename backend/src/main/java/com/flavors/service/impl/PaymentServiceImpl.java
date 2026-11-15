package com.flavors.service.impl;

import com.flavors.dto.EscrowResponse;
import com.flavors.dto.HostedPaymentRequest;
import com.flavors.dto.HostedPaymentSession;
import com.flavors.dto.PaymentAttempt;
import com.flavors.dto.PaymentResult;
import com.flavors.dto.PaymentStartResponse;
import com.flavors.entity.Campaign;
import com.flavors.entity.EscrowPayment;
import com.flavors.entity.Notification;
import com.flavors.entity.PaymentTransaction;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.EscrowStatus;
import com.flavors.enums.PaymentTransactionStatus;
import com.flavors.enums.PaymentTransactionType;
import com.flavors.enums.ProviderPaymentStatus;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.CampaignResponseStateException;
import com.flavors.exception.PaymentAlreadyPaidException;
import com.flavors.exception.PaymentNotFoundException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.EscrowPaymentRepository;
import com.flavors.repository.NotificationRepository;
import com.flavors.repository.PaymentTransactionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.IdempotencyService;
import com.flavors.service.PaymentProvider;
import com.flavors.service.PaymentService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Paying for a campaign and the escrow (UC-F7.1). Design: docs/Architecture.md §17.
 *
 * <p>The provider is only asked for the status, never trusted: a callback or the browser's return only makes Flavors
 * ask. Every change to a campaign's payment takes the campaign row lock first ({@code findByIdForUpdate}), then
 * re-reads the payment, so a repeated or concurrent answer finds the work already done and changes nothing.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /** Every amount is USD (FR-F7.2). */
    static final String CURRENCY = "USD";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    private final CampaignRepository campaigns;
    private final EscrowPaymentRepository escrows;
    private final PaymentTransactionRepository transactions;
    private final NotificationRepository notifications;
    private final AuditLogger auditLogger;
    private final IdempotencyService idempotency;
    private final PaymentProvider provider;
    private final TransactionOperations tx;
    private final String appBaseUrl;
    private final Clock clock;

    @Autowired
    public PaymentServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
                              PaymentTransactionRepository transactions, NotificationRepository notifications,
                              AuditLogger auditLogger, IdempotencyService idempotency, PaymentProvider provider,
                              TransactionOperations tx, @Value("${flavors.app.base-url}") String appBaseUrl) {
        this(campaigns, escrows, transactions, notifications, auditLogger, idempotency, provider, tx, appBaseUrl,
                Clock.systemUTC());
    }

    PaymentServiceImpl(CampaignRepository campaigns, EscrowPaymentRepository escrows,
                       PaymentTransactionRepository transactions, NotificationRepository notifications,
                       AuditLogger auditLogger, IdempotencyService idempotency, PaymentProvider provider,
                       TransactionOperations tx, String appBaseUrl, Clock clock) {
        this.campaigns = campaigns;
        this.escrows = escrows;
        this.transactions = transactions;
        this.notifications = notifications;
        this.auditLogger = auditLogger;
        this.idempotency = idempotency;
        this.provider = provider;
        this.tx = tx;
        this.appBaseUrl = stripTrailingSlash(appBaseUrl);
        this.clock = clock;
    }

    @Override
    public PaymentStartResponse startPayment(long brandId, long campaignId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ValidationFailedException("Idempotency-Key",
                    "Send an Idempotency-Key header of 1 to " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters.");
        }

        // The row changes happen in the idempotency transaction; the provider call comes after it, because an
        // external call can't be rolled back (docs/Architecture.md §7). Calling the provider again for the same
        // attempt is safe: its reference is fixed.
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean created = new AtomicBoolean();
        PaymentAttempt attempt = idempotency.execute("brand-payment:" + brandId, idempotencyKey,
                "campaign:" + campaignId,
                () -> {
                    ran.set(true);
                    return openAttempt(brandId, campaignId, idempotencyKey, created);
                }, PaymentAttempt.class);
        // An attempt this very call created cannot have been paid yet. Any other one (an open attempt a second click
        // reuses, or the attempt of a replayed key) may have been paid at the provider while the return or callback was
        // not processed yet, so the provider is asked first, under the same rules as the callback.
        if (!(ran.get() && created.get())) {
            attempt = settleBeforeReopening(brandId, campaignId, idempotencyKey, attempt);
        }
        assertStillPayable(campaignId, attempt.transactionId());

        HostedPaymentSession session = provider.startHostedPayment(new HostedPaymentRequest(
                attempt.reference(),
                attempt.amountCents(),
                CURRENCY,
                "Flavors campaign " + campaignId,
                appBaseUrl + "/api/public/payments/callback?externalId=" + attempt.reference(),
                appBaseUrl + "/api/public/payments/return?externalId=" + attempt.reference()));
        return new PaymentStartResponse(session.checkoutUrl());
    }

    /** What a Pay click does with an attempt that may already have been paid, from the provider's answer. */
    enum StartAction {
        /** Paid in time and held through the normal path: no new checkout. */
        ALREADY_PAID,
        /** Paid, but after the window: recorded, not held, no new checkout. */
        RECEIVED_NOT_HELD,
        /** Still waiting for the payer: reopen the same checkout. */
        REOPEN,
        /** The provider ended that attempt without payment: open a fresh one. */
        NEW_ATTEMPT
    }

    static StartAction actionFor(PaymentResult result) {
        return switch (result.status()) {
            case SUCCEEDED -> result.held() ? StartAction.ALREADY_PAID : StartAction.RECEIVED_NOT_HELD;
            case FAILED -> StartAction.NEW_ATTEMPT;
            case PENDING -> StartAction.REOPEN;
        };
    }

    /**
     * Asks the provider about a reused attempt before its checkout is opened again. A provider error is not an answer:
     * it propagates (a retryable 502), so the click opens nothing and no second charge can follow.
     */
    private PaymentAttempt settleBeforeReopening(long brandId, long campaignId, String idempotencyKey,
                                                 PaymentAttempt attempt) {
        PaymentResult result = reconcile(attempt.reference()).orElseThrow(PaymentNotFoundException::new);
        return switch (actionFor(result)) {
            case ALREADY_PAID -> throw new PaymentAlreadyPaidException();
            case RECEIVED_NOT_HELD -> throw new CampaignResponseStateException(
                    "Your payment arrived after the time to pay ended, so it was not held.");
            case REOPEN -> attempt;
            case NEW_ATTEMPT -> tx.execute(s -> openAttempt(brandId, campaignId, idempotencyKey, new AtomicBoolean()));
        };
    }

    /** A replayed key returns the attempt of its first call, so the live campaign and attempt are checked under the lock. */
    private void assertStillPayable(long campaignId, long transactionId) {
        tx.execute(s -> {
            Campaign campaign = campaigns.findByIdForUpdate(campaignId).orElseThrow(CampaignNotFoundException::new);
            if (campaign.getStatus() != CampaignStatus.AWAITING_PAYMENT
                    || !campaign.isPaymentWindowOpen(clock.instant())) {
                throw new CampaignResponseStateException("This can't be paid now: the payment window has closed.");
            }
            PaymentTransaction live = transactions.findById(transactionId).orElseThrow(PaymentNotFoundException::new);
            if (live.getStatus() != PaymentTransactionStatus.PENDING) {
                throw new CampaignResponseStateException("This payment attempt already ended. Start a new payment.");
            }
            return Boolean.TRUE;
        });
    }

    /** Runs inside the idempotency transaction: database work only. */
    private PaymentAttempt openAttempt(long brandId, long campaignId, String idempotencyKey, AtomicBoolean created) {
        Instant now = clock.instant();
        Campaign campaign = campaigns.findByIdForUpdate(campaignId)
                .filter(c -> Objects.equals(c.getBrandId(), brandId))
                .orElseThrow(CampaignNotFoundException::new);
        if (campaign.getStatus() != CampaignStatus.AWAITING_PAYMENT) {
            throw new CampaignResponseStateException(
                    "This can't be paid: the campaign is " + campaign.getStatus() + ", not AWAITING_PAYMENT.");
        }
        if (!campaign.isPaymentWindowOpen(now)) {
            throw new CampaignResponseStateException("The time to pay for this campaign has passed.");
        }

        EscrowPayment escrow = escrows.findByCampaignId(campaignId).orElse(null);
        if (escrow == null) {
            escrow = escrows.save(new EscrowPayment(campaignId,
                    Objects.requireNonNull(campaign.getAgreedPriceCents()), provider.name(), now));
        }

        // One open attempt at a time: a second Pay click (a second key) reuses the open attempt, so nobody is charged twice.
        PaymentTransaction open = transactions
                .findByEscrowIdAndTypeAndStatusOrderByIdDesc(escrow.getId(), PaymentTransactionType.CHARGE,
                        PaymentTransactionStatus.PENDING)
                .stream().findFirst().orElse(null);
        if (open == null) {
            String reference = UUID.randomUUID().toString();
            open = transactions.save(new PaymentTransaction(escrow.getId(), PaymentTransactionType.CHARGE,
                    escrow.getAmountCents(), reference, idempotencyKey));
            escrow.attachProviderPayment(reference, now);
            created.set(true);
            auditLogger.record("PAYMENT_STARTED", "campaign", campaignId, Map.of("transactionId", open.getId()));
        }
        return new PaymentAttempt(open.getId(), open.getProviderRef(), escrow.getAmountCents(), open.getStatus());
    }

    @Override
    public Optional<PaymentResult> reconcile(String reference) {
        Optional<PaymentTransaction> known = transactions.findByProviderRef(reference);
        if (known.isEmpty()) {
            return Optional.empty();
        }
        if (known.get().getStatus() == PaymentTransactionStatus.SUCCEEDED) {
            // Already recorded: a repeated callback needs no provider call and records nothing new.
            return resultFor(reference);
        }

        // Outside any transaction: the provider call must not hold a lock.
        ProviderPaymentStatus status = provider.queryStatus(reference, known.get().getAmountCents(), CURRENCY);
        return tx.execute(s -> applyStatus(reference, status));
    }

    /** Runs inside a transaction, under the campaign lock. */
    private Optional<PaymentResult> applyStatus(String reference, ProviderPaymentStatus status) {
        Optional<Long> campaignId = transactions.findCampaignIdByProviderRef(reference);
        if (campaignId.isEmpty()) {
            return Optional.empty();
        }
        Campaign campaign = campaigns.findByIdForUpdate(campaignId.get()).orElseThrow(CampaignNotFoundException::new);
        // Read after the lock: a concurrent answer that committed first is visible now.
        PaymentTransaction payment = transactions.findByProviderRef(reference).orElseThrow(PaymentNotFoundException::new);

        if (status == ProviderPaymentStatus.PENDING) {
            return Optional.of(resultOf(campaign.getId(), payment));
        }
        if (status == ProviderPaymentStatus.FAILED) {
            if (payment.getStatus() == PaymentTransactionStatus.PENDING) {
                payment.fail();
                auditLogger.record("PAYMENT_FAILED", "campaign", campaign.getId(),
                        Map.of("transactionId", payment.getId()));
            }
            return Optional.of(resultOf(campaign.getId(), payment));
        }

        // PAID. A second PAID for a payment already recorded is the same payment (EX-F7.1.3): nothing more happens.
        if (payment.getStatus() == PaymentTransactionStatus.SUCCEEDED) {
            return Optional.of(resultOf(campaign.getId(), payment));
        }
        Instant now = clock.instant();
        EscrowPayment escrow = escrows.findById(payment.getEscrowId()).orElseThrow(PaymentNotFoundException::new);
        payment.succeed();

        if (campaign.getStatus() == CampaignStatus.AWAITING_PAYMENT && campaign.isPaymentWindowOpen(now)
                && escrow.getStatus() == EscrowStatus.PENDING) {
            escrow.hold(now);
            campaign.activate(now);
            auditLogger.record("ESCROW_HELD", "campaign", campaign.getId(), Map.of("transactionId", payment.getId()));
            auditLogger.record("CAMPAIGN_ACTIVATED", "campaign", campaign.getId());
            String message = "The payment for campaign " + campaign.getName()
                    + " is held in escrow, and the campaign is active.";
            notify(campaign.getBrandId(), campaign, Notification.ESCROW_HELD, message);
            notify(campaign.getCreatorId(), campaign, Notification.ESCROW_HELD, message);
        } else {
            // The money arrived after Timer B (or for a campaign that is no longer waiting). Nothing is held
            // automatically: the record stays so that EEC-45 (refunds) can return it to the brand.
            LOGGER.warn("Payment {} was received for campaign {} in status {} after its payment window; not held.",
                    payment.getId(), campaign.getId(), campaign.getStatus());
            auditLogger.record("PAYMENT_NOT_HELD", "campaign", campaign.getId(), Map.of(
                    "transactionId", payment.getId(),
                    "campaignStatus", campaign.getStatus().name()));
        }
        return Optional.of(resultOf(campaign.getId(), payment));
    }

    private Optional<PaymentResult> resultFor(String reference) {
        return transactions.findCampaignIdByProviderRef(reference).flatMap(campaignId ->
                transactions.findByProviderRef(reference)
                        .map(p -> resultOf(campaignId, p)));
    }

    /** Held only when the escrow is held: a payment recorded after Timer B is SUCCEEDED but not held. */
    private PaymentResult resultOf(Long campaignId, PaymentTransaction payment) {
        boolean held = escrows.findById(payment.getEscrowId())
                .map(e -> e.getStatus() == EscrowStatus.HELD)
                .orElse(false);
        return new PaymentResult(campaignId, payment.getStatus(), held);
    }

    @Override
    public int reconcileOpenPayments(Duration olderThan) {
        Instant before = clock.instant().minus(olderThan);
        int checked = 0;
        for (String reference : transactions.findReferencesToReconcile(CampaignStatus.AWAITING_PAYMENT,
                PaymentTransactionStatus.PENDING, before)) {
            try {
                reconcile(reference);
                checked++;
            } catch (RuntimeException e) {
                LOGGER.warn("Could not reconcile a payment; it will be checked again on the next run.", e);
            }
        }
        return checked;
    }

    /** Timer B. Each campaign is expired in its own transaction, so one failure cannot undo the others. */
    @Override
    public int expireOverduePayments() {
        Instant now = clock.instant();
        int expired = 0;
        for (Long campaignId : campaigns.findOverduePaymentIds(CampaignStatus.AWAITING_PAYMENT, now)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> expireOnePayment(campaignId, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not expire the payment window of campaign {}; it will be tried again on the next run.",
                        campaignId, e);
            }
        }
        return expired;
    }

    private boolean expireOnePayment(Long campaignId, Instant now) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId).orElse(null);
        if (campaign == null || campaign.getStatus() != CampaignStatus.AWAITING_PAYMENT
                || campaign.getPaymentDeadline() == null || campaign.getPaymentDeadline().isAfter(now)) {
            return false;
        }
        EscrowPayment escrow = escrows.findByCampaignId(campaignId).orElse(null);
        if (escrow != null && escrow.getStatus() != EscrowStatus.PENDING) {
            return false;
        }
        if (escrow != null) {
            // Unfinished attempts are closed, so a late answer for them is recorded as not held rather than reopening the campaign.
            for (PaymentTransaction open : transactions.findByEscrowIdAndTypeAndStatusOrderByIdDesc(
                    escrow.getId(), PaymentTransactionType.CHARGE, PaymentTransactionStatus.PENDING)) {
                open.fail();
            }
        }
        campaign.expirePayment(now);
        auditLogger.record("PAYMENT_WINDOW_EXPIRED", "campaign", campaignId);

        String message = "Campaign " + campaign.getName()
                + " expired: the payment was not made within 3 days. Nothing was charged.";
        notify(campaign.getBrandId(), campaign, Notification.CAMPAIGN_PAYMENT_EXPIRED, message);
        notify(campaign.getCreatorId(), campaign, Notification.CAMPAIGN_PAYMENT_EXPIRED, message);
        return true;
    }

    @Override
    public EscrowResponse getEscrowForBrand(long brandId, long campaignId) {
        campaigns.findByIdAndBrandId(campaignId, brandId).orElseThrow(CampaignNotFoundException::new);
        return escrowOf(campaignId);
    }

    @Override
    public EscrowResponse getEscrowForCreator(long creatorId, long campaignId) {
        campaigns.findById(campaignId)
                .filter(c -> Objects.equals(c.getCreatorId(), creatorId))
                .filter(c -> c.getStatus() != CampaignStatus.DRAFT)
                .orElseThrow(CampaignNotFoundException::new);
        return escrowOf(campaignId);
    }

    private EscrowResponse escrowOf(long campaignId) {
        EscrowPayment escrow = escrows.findByCampaignId(campaignId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment has been started for this campaign."));
        return new EscrowResponse(campaignId, escrow.getStatus(), escrow.getAmountCents(), escrow.getHeldAt(),
                escrow.getReleasedAt(), escrow.getUpdatedAt());
    }

    private void notify(Long userId, Campaign campaign, String type, String message) {
        notifications.save(new Notification(userId, campaign.getId(), type, message));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
