package com.flavors.mapper;

import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.CreatorSocialAccountResponse;
import com.flavors.dto.PendingCreatorResponse;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import com.flavors.enums.SocialAccountStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CreatorReviewMapper {

    public PendingCreatorResponse toPending(CreatorProfile profile) {
        return new PendingCreatorResponse(profile.getUserId(), profile.getDisplayName(), profile.getFoodCategory(),
                profile.getLocation(), profile.getCreatedAt());
    }

    public CreatorReviewResponse toReview(CreatorProfile profile, String email, List<SocialAccount> accounts) {
        List<CreatorSocialAccountResponse> accountResponses = accounts.stream()
                .map(account -> new CreatorSocialAccountResponse(account.getPlatform(), account.getExternalId(),
                        account.getStatus(), account.getFollowers(), account.getEngagementRate(),
                        account.getLastPulledAt()))
                .toList();
        return new CreatorReviewResponse(profile.getUserId(), email, profile.getDisplayName(), profile.getBio(),
                profile.getFoodCategory(), profile.getLocation(), profile.getPriceMinCents(),
                profile.getPriceMaxCents(), profile.getVerificationStatus(), profile.getRejectionReason(),
                needsReconnection(accounts), profile.getCreatedAt(), profile.getReviewedAt(), accountResponses);
    }

    /** True when the social numbers can't be trusted: no account, or one that is not connected (EX-F20.1.2). */
    public static boolean needsReconnection(List<SocialAccount> accounts) {
        return accounts.isEmpty()
                || accounts.stream().anyMatch(account -> account.getStatus() != SocialAccountStatus.CONNECTED);
    }
}
