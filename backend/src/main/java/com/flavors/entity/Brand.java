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

    /** The matching {@link AppUser} id. */
    @Id
    private Long userId;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String contactPerson;

    @Column(nullable = false)
    private String phone;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
