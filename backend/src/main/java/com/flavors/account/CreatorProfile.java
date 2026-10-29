package com.flavors.account;
 
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
 
/**
 * A creator's profile (table {@code creator_profile}, V2). Its id is the creator's {@link AppUser} id,
 * so it is assigned by the caller, not generated.
 */
@Entity
public class CreatorProfile {
 
    @Id
    private Long userId;
 
    @Column(nullable = false)
    private String displayName;
 
    private String bio;
 
    @Column(nullable = false)
    private String foodCategory;
 
    @Column(nullable = false)
    private String location;
 
    @Column(nullable = false)
    private long priceMinCents;
 
    @Column(nullable = false)
    private long priceMaxCents;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_APPROVAL;
 
    private String rejectionReason;
 
    /** The admin who approved or rejected the creator. */
    private Long reviewedBy;
 
    private Instant reviewedAt;
 
    private String storefrontSlug;
 
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
 
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
 
    /** For JPA only. */
    protected CreatorProfile() {
    }
 
    public CreatorProfile(Long userId, String displayName, String foodCategory, String location,
                          long priceMinCents, long priceMaxCents) {
        checkDetails(foodCategory, location, priceMinCents, priceMaxCents);
        this.userId = userId;
        this.displayName = displayName;
        this.foodCategory = foodCategory;
        this.location = location;
        this.priceMinCents = priceMinCents;
        this.priceMaxCents = priceMaxCents;
    }
 
    /**
     * The creator edits her own portfolio details (UC-F1.2, FR-F1.7). An invalid edit throws and
     * changes nothing (EX-F1.2.2).
     */
    public void updateDetails(String bio, String foodCategory, String location,
                              long priceMinCents, long priceMaxCents) {
        checkDetails(foodCategory, location, priceMinCents, priceMaxCents);
        this.bio = bio;
        this.foodCategory = foodCategory;
        this.location = location;
        this.priceMinCents = priceMinCents;
        this.priceMaxCents = priceMaxCents;
    }
 
    private static void checkDetails(String foodCategory, String location, long priceMinCents, long priceMaxCents) {
        if (foodCategory == null || foodCategory.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is required.");
        }
        if (priceMinCents < 0) {
            throw new IllegalArgumentException("Minimum price can't be negative.");
        }
        if (priceMinCents > priceMaxCents) {
            throw new IllegalArgumentException("Minimum price can't be above the maximum price.");
        }
    }
 
    public Long getUserId() {
        return userId;
    }
 
    public String getDisplayName() {
        return displayName;
    }
 
    public String getBio() {
        return bio;
    }
 
    public String getFoodCategory() {
        return foodCategory;
    }
 
    public String getLocation() {
        return location;
    }
 
    public long getPriceMinCents() {
        return priceMinCents;
    }
 
    public long getPriceMaxCents() {
        return priceMaxCents;
    }
 
    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }
 
    public String getRejectionReason() {
        return rejectionReason;
    }
 
    public Long getReviewedBy() {
        return reviewedBy;
    }
 
    public Instant getReviewedAt() {
        return reviewedAt;
    }
 
    public String getStorefrontSlug() {
        return storefrontSlug;
    }
 
    public Instant getCreatedAt() {
        return createdAt;
    }
 
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}