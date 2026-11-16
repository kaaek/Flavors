package com.flavors.repository;

import com.flavors.dto.ProofReviewAgreementResponse;
import com.flavors.exception.DeliveryProofNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only projection of existing V3 terms; does not create a second campaign entity or editing service. */
@Repository
public class ProofReviewAgreementRepository {

    private final JdbcTemplate jdbc;

    public ProofReviewAgreementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The caller must check campaign ownership first. All client data is bound as a parameter. */
    public ProofReviewAgreementResponse find(long campaignId) {
        var deliverables = jdbc.query("""
                SELECT platform, type, count FROM campaign_deliverable WHERE campaign_id = ? ORDER BY id
                """, (row, index) -> new ProofReviewAgreementResponse.Deliverable(
                        row.getString("platform"), row.getString("type"), row.getInt("count")), campaignId);
        return jdbc.query("""
                SELECT start_date, last_posting_date, content_requirements FROM campaign WHERE id = ?
                """, (row, index) -> new ProofReviewAgreementResponse(
                        row.getObject("start_date", java.time.LocalDate.class),
                        row.getObject("last_posting_date", java.time.LocalDate.class),
                        row.getString("content_requirements"), deliverables), campaignId)
                .stream().findFirst().orElseThrow(DeliveryProofNotFoundException::new);
    }
}
