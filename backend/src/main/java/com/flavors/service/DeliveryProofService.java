package com.flavors.service;

import com.flavors.dto.DeliveryProofResponse;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.enums.Role;
import java.util.List;

/** Creator delivery proof (EEC-46, UC-F5.1). */
public interface DeliveryProofService {

    DeliveryProofResponse getForCreator(long creatorId, long campaignId);

    DeliveryProofResponse saveDraft(long creatorId, long campaignId, ProofDraftRequest request,
                                    List<ProofUpload> newFiles);

    DeliveryProofResponse submit(long creatorId, long campaignId, ProofDraftRequest request,
                                 List<ProofUpload> newFiles);

    /** Submitted versions only; a brand never sees the creator's draft. */
    DeliveryProofResponse getForBrand(long brandId, long campaignId);

    /** Submitted versions only; the later dispute/admin UI can reuse this endpoint. */
    DeliveryProofResponse getForAdmin(long campaignId);

    ProofFileContent download(long userId, Role role, long fileId);
}
