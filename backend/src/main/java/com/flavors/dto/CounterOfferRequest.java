package com.flavors.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CounterOfferRequest(
        @Positive(message = "Counter-offer price must be more than 0.")
        Long priceCents,

        @Size(min = 1, message = "At least one deliverable is required when changing deliverables.")
        List<@Valid DeliverableItem> deliverables) {
}