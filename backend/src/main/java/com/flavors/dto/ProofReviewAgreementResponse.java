package com.flavors.dto;

import com.flavors.enums.DeliverablePlatform;
import com.flavors.enums.DeliverableType;
import java.time.LocalDate;
import java.util.List;

/** Agreed terms the brand checks manually against the submitted posts (FR-F6.1). */
public record ProofReviewAgreementResponse(LocalDate startDate, LocalDate lastPostingDate,
        String contentRequirements, boolean screenshotsRequired, List<Deliverable> deliverables) {

    public record Deliverable(DeliverablePlatform platform, DeliverableType type, int count) {
    }
}
