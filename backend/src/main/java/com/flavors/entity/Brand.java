package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A restaurant account's details (table {@code brand}, V2). */
@Entity
public class Brand {

    /** Same as {@code cuisine varchar(50)} in V8. */
    public static final int MAX_CUISINE_LENGTH = 50;

    /** The description column has no length limit in the database, so the entity sets one. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    /** The matching {@link AppUser} id. */
    @Id
    private Long userId;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String contactPerson;

    @Column(nullable = false)
    private String phone;

    /** What the restaurant serves (V8), so creators can search for it. Optional. */
    @Column(length = MAX_CUISINE_LENGTH)
    private String cuisine;

    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Brand() {
    }

    public Brand(Long userId, String businessName, String contactPerson, String phone) {
        this.userId = userId;
        this.businessName = businessName;
        this.contactPerson = contactPerson;
        this.phone = phone;
    }

    public Brand(Long userId, String businessName, String contactPerson, String phone,
                 String cuisine, String description) {
        this(userId, businessName, contactPerson, phone);
        updateProfile(cuisine, description);
    }

    /** The brand edits what creators see about it; blank values are stored as "not set". */
    public void updateProfile(String cuisine, String description) {
        String cleanCuisine = blankToNull(cuisine);
        String cleanDescription = blankToNull(description);
        if (cleanCuisine != null && cleanCuisine.length() > MAX_CUISINE_LENGTH) {
            throw new IllegalArgumentException("Cuisine can't be longer than " + MAX_CUISINE_LENGTH + " characters.");
        }
        if (cleanDescription != null && cleanDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Description can't be longer than " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        this.cuisine = cleanCuisine;
        this.description = cleanDescription;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public String getCuisine() {
        return cuisine;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
