
package com.flavors.entity;

import java.io.Serializable;
import java.util.Objects;

public class SavedCreatorId implements Serializable {

    private Long brandId;
    private Long creatorId;

    public SavedCreatorId() {
    }

    public SavedCreatorId(Long brandId, Long creatorId) {
        this.brandId = brandId;
        this.creatorId = creatorId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SavedCreatorId other)) {
            return false;
        }

        return Objects.equals(brandId, other.brandId)
                && Objects.equals(creatorId, other.creatorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(brandId, creatorId);
    }
}
