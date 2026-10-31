package com.flavors.service;

import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.PendingCreatorResponse;
import java.util.List;

/** UC-F20.1: the admin reviews creator profiles that wait for approval. */
public interface CreatorReviewService {

    /** Profiles waiting for approval, oldest first (FR-F20.1). */
    List<PendingCreatorResponse> listPending();

    /** Registration details and social numbers of one creator (FR-F20.2). */
    CreatorReviewResponse get(long creatorId);

    /** Approves a pending profile, unless her social account needs reconnection (FR-F20.3, EX-F20.1.2). */
    CreatorReviewResponse approve(long adminId, long creatorId);

    /** Rejects a pending profile with a written reason (EX-F20.1.1). */
    CreatorReviewResponse reject(long adminId, long creatorId, String reason);
}
