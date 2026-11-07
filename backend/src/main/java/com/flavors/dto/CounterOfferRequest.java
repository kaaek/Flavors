
package com.flavors.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CounterOfferRequest(
        @PositiveOrZero(message = "Counter-offer price cannot be negative.")
        Long priceCents,

        @Size(min = 1, message = "At least one deliverable is required when changing deliverables.")
        List<@Valid DeliverableItem> deliverables) {
}
