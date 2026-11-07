package com.flavors.entity;

import com.flavors.enums.CampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
public class Campaign {

    private static final long RESPONSE_PERIOD_SECONDS = 3L * 24 * 60 * 60;
    private static final long PAYMENT_PERIOD_SECONDS = 3L * 24 * 60 * 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    /** Empty while the campaign is a draft. */
    private Long creatorId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignStatus status;
    /** The brand's original offer, stored in cents. */
    private Long priceOfferedCents;

    /** The final amount to be paid to the creator, stored in cents. */
    private Long agreedPriceCents;

    private LocalDate startDate;

    private LocalDate lastPostingDate;

    private Instant sentAt;

    /** Timer A: the creator's response deadline. */
    private Instant responseDeadline;

    private Instant acceptedAt;

    /** Timer B: the brand's payment deadline. */
    private Instant paymentDeadline;

    /** For JPA only. */
    protected Campaign() {
    }

    /** Sends a draft campaign and starts Timer A. */
    public void sendToCreator(Instant now) {
    requireStatus(CampaignStatus.DRAFT);
    Objects.requireNonNull(now);

    if (creatorId == null || priceOfferedCents == null) {
        throw new IllegalStateException("Campaign must have a creator and an offered price.");
    }

    if (startDate == null || lastPostingDate == null
            || startDate.isAfter(lastPostingDate)) {
        throw new IllegalStateException("Campaign must have valid start and posting dates.");
    }

    sentAt = now;
    responseDeadline = now.plusSeconds(RESPONSE_PERIOD_SECONDS);
    status = CampaignStatus.AWAITING_CREATOR_RESPONSE;
}

    public void accept(Instant now) {
        requireStatus(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireBeforeDeadline(now);
        agreedPriceCents = Objects.requireNonNull(priceOfferedCents);
        startPaymentPeriod(now);
    }

    public void reject(Instant now) {
        requireStatus(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireBeforeDeadline(now);
        status = CampaignStatus.REJECTED;
    }

    public void submitCounterOffer(Instant now) {
        requireStatus(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        requireBeforeDeadline(now);
        status = CampaignStatus.COUNTERED;
    }

    public void approveCounterOffer(Long counterPriceCents, Instant now) {
        requireStatus(CampaignStatus.COUNTERED);
        if (counterPriceCents == null || counterPriceCents < 0) {
            throw new IllegalArgumentException("Counter-offer price must be non-negative.");
        }
        agreedPriceCents = counterPriceCents;
        startPaymentPeriod(now);
    }

    public void declineCounterOffer() {
        requireStatus(CampaignStatus.COUNTERED);
        status = CampaignStatus.DECLINED;
    }

    public void expireResponse(Instant now) {
        requireStatus(CampaignStatus.AWAITING_CREATOR_RESPONSE);
        if (responseDeadline == null || responseDeadline.isAfter(now)) {
            throw new IllegalStateException("The response deadline has not passed.");
        }
        status = CampaignStatus.EXPIRED;
    }

    private void startPaymentPeriod(Instant now) {
        acceptedAt = Objects.requireNonNull(now);
        paymentDeadline = now.plusSeconds(PAYMENT_PERIOD_SECONDS);
        status = CampaignStatus.AWAITING_PAYMENT;
    }

    private void requireBeforeDeadline(Instant now) {
        if (responseDeadline == null || !responseDeadline.isAfter(now)) {
            throw new IllegalStateException("The creator response deadline has passed.");
        }
    }

    private void requireStatus(CampaignStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Campaign is not in the required status.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getName() {
        return name;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Long getPriceOfferedCents() {
        return priceOfferedCents;
    }

    public Long getAgreedPriceCents() {
        return agreedPriceCents;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getResponseDeadline() {
        return responseDeadline;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getPaymentDeadline() {
        return paymentDeadline;
    }
}
