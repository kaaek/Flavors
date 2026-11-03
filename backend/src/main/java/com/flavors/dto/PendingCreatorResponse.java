package com.flavors.dto;

import java.time.Instant;

/** One row of the admin queue (FR-F20.1). */
public record PendingCreatorResponse(long id, String displayName, String foodCategory, String location,
                                     Instant registeredAt) {
}
