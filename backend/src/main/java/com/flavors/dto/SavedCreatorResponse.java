package com.flavors.dto;
import java.time.OffsetDateTime;
public record SavedCreatorResponse(
        Long creatorId,
        OffsetDateTime savedAt
) {
}  
