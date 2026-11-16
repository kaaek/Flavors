package com.flavors.dto;

import java.time.LocalDate;
import java.util.List;

/** Agreed terms the brand checks manually against the submitted posts (FR-F6.1). */
public record ProofReviewAgreementResponse(LocalDate startDate, LocalDate lastPostingDate,
        String contentRequirements, List<Deliverable> deliverables) {

    public record Deliverable(String platform, String type, int count) {
    }
}
