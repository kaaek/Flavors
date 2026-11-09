package com.flavors.entity;

import com.flavors.enums.CampaignStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A campaign (table {@code campaign}, V3), with the branches it runs at ({@code campaign_branch}) and
 * its deliverables ({@code campaign_deliverable}).
 *
 * <p>This class maps the columns the stories so far use: the portfolio reads a few of them, and the
 * brand's create-and-send story (EEC-40) writes the draft and sends it. The later stories (the
 * creator's answer, payment, proof...) add the other columns and the methods that move the status on.
 */
@Entity
public class Campaign {

    /** Timer A: how long the creator has to answer a request (FR-F4.5). */
    public static final Duration RESPONSE_WINDOW = Duration.ofDays(3);

    /** Same as {@code name varchar(150)} in V3. */
    public static final int MAX_NAME_LENGTH = 150;

    /** The content_requirements column has no length limit in the database, so the entity sets one. */
    public static final int MAX_CONTENT_REQUIREMENTS_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    /** Empty while the campaign is a draft. */
    private Long creatorId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    /** The brand's budget: what it offers the creator, in cents. Empty in a draft that has none yet. */
    private Long priceOfferedCents;

    /** What is actually paid to the creator; set when she accepts. */
    private Long agreedPriceCents;

    private LocalDate startDate;

    private LocalDate lastPostingDate;

    private String contentRequirements;

    /** When the brand sent the request; empty for a draft. */
    private Instant sentAt;

    /** Timer A: {@link #sentAt} + {@link #RESPONSE_WINDOW}. */
    private Instant responseDeadline;

    @ElementCollection
    @CollectionTable(name = "campaign_branch", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<Long> branchIds = new HashSet<>();

    // nullable = false makes Hibernate insert the row with its campaign_id at once, instead of inserting it
    // empty and updating it after (the column is NOT NULL).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "campaign_id", nullable = false)
    private List<CampaignDeliverable> deliverables = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected Campaign() {
    }

    /** A new, empty draft. Fill it with {@link #saveDraft}. */
    public Campaign(Long brandId, String name, Instant now) {
        this.brandId = brandId;
        this.name = name;
        this.status = CampaignStatus.DRAFT;
        this.updatedAt = now;
    }

    /**
     * Replaces everything the brand can edit while the campaign is a draft. The caller has validated
     * the values; a draft may leave all but the name empty.
     */
    public void saveDraft(String name, Long creatorId, Long priceOfferedCents, LocalDate startDate,
                          LocalDate lastPostingDate, String contentRequirements, Collection<Long> branchIds,
                          Collection<CampaignDeliverable> deliverables, Instant now) {
        requireDraft();
        this.name = name;
        this.creatorId = creatorId;
        this.priceOfferedCents = priceOfferedCents;
        this.startDate = startDate;
        this.lastPostingDate = lastPostingDate;
        this.contentRequirements = contentRequirements;
        this.branchIds.retainAll(branchIds);
        this.branchIds.addAll(branchIds);
        this.deliverables.clear();
        this.deliverables.addAll(deliverables);
        this.updatedAt = now;
    }

    /**
     * Sends the request: the creator now has {@link #RESPONSE_WINDOW} to answer (Timer A). The caller has
     * checked that the campaign is complete.
     */
    public void send(Instant now) {
        requireDraft();
        this.status = CampaignStatus.AWAITING_CREATOR_RESPONSE;
        this.sentAt = now;
        this.responseDeadline = now.plus(RESPONSE_WINDOW);
        this.updatedAt = now;
    }

    public boolean isDraft() {
        return status == CampaignStatus.DRAFT;
    }

    private void requireDraft() {
        if (!isDraft()) {
            throw new IllegalStateException("Campaign " + id + " is " + status + ", not a draft");
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getLastPostingDate() {
        return lastPostingDate;
    }

    public String getContentRequirements() {
        return contentRequirements;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getResponseDeadline() {
        return responseDeadline;
    }

    public Set<Long> getBranchIds() {
        return Set.copyOf(branchIds);
    }

    public List<CampaignDeliverable> getDeliverables() {
        return List.copyOf(deliverables);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
