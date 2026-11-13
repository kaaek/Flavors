package com.flavors.service;

import com.flavors.dto.CampaignCreatorResponse;
import com.flavors.dto.CampaignRequest;
import com.flavors.dto.CampaignResponse;
import com.flavors.dto.CampaignSummaryResponse;
import java.util.List;

/**
 * A brand creates a campaign, saves it as a draft and sends it to one creator (UC-F3.1). Every method
 * takes the brand's id from the login, never from the request, and only ever sees that brand's campaigns.
 */
public interface CampaignService {

    /** The brand's campaigns, most recently changed first. */
    List<CampaignSummaryResponse> list(long brandId);

    /** @throws com.flavors.exception.CampaignNotFoundException if it doesn't exist or isn't this brand's */
    CampaignResponse get(long brandId, long campaignId);

    /** The approved creator a form opens with. @throws com.flavors.exception.CreatorNotFoundException otherwise */
    CampaignCreatorResponse findCreator(long creatorId);

    /**
     * Saves a new draft: only the name is required (FR-F3.4). The creator is not told (EX-F3.1.2).
     *
     * @throws com.flavors.exception.NoBranchException if the brand has no branch (EX-F3.1.3)
     * @throws com.flavors.exception.CampaignValidationException with a message per wrong field
     */
    CampaignResponse saveDraft(long brandId, CampaignRequest request);

    /**
     * Replaces the details of a draft.
     *
     * @throws com.flavors.exception.CampaignNotEditableException if it was already sent
     */
    CampaignResponse updateDraft(long brandId, long campaignId, CampaignRequest request);

    /**
     * Creates a campaign and sends it in one step (the form was never saved as a draft). Sending is refused
     * with a message per missing field, and then nothing is saved or sent (EX-F3.1.1).
     */
    CampaignResponse send(long brandId, CampaignRequest request);

    /**
     * Saves the given details into an existing draft and sends it: the status becomes
     * AWAITING_CREATOR_RESPONSE, Timer A (3 days) starts and the creator gets an in-app notification, all
     * in one transaction (FR-F3.2, FR-F3.5). If a field is missing the draft is left as it was.
     *
     * @throws com.flavors.exception.CampaignNotEditableException if it was already sent
     */
    CampaignResponse send(long brandId, long campaignId, CampaignRequest request);
}
