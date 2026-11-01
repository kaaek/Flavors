package com.flavors.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;

@Entity
@Table(name = "saved_creator")
@IdClass(SavedCreatorId.class)
public class SavedCreator {

    @Id
    @Column(name = "brand_id")
    private Long brandId;
    @Id
    @Column(name = "creator_id")
    private Long creatorId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SavedCreator() {
    }
    public SavedCreator(Long brandId, Long creatorId) {
        this.brandId = brandId;
        this.creatorId = creatorId;
    }
    public Long getBrandId() {
        return brandId;
    }
    public Long getCreatorId() {
        return creatorId;
    }
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
