package com.flavors.mapper;

import com.flavors.dto.SocialAccountResponse;
import com.flavors.entity.SocialAccount;
import org.springframework.stereotype.Component;

/** Converts a social account into what the creator's page shows (never the token). */
@Component
public class SocialAccountMapper {

    public SocialAccountResponse toResponse(SocialAccount account) {
        return new SocialAccountResponse(
                account.getId(),
                account.getPlatform(),
                account.getStatus(),
                account.getFollowers(),
                account.getEngagementRate(),
                account.getLastPulledAt());
    }
}
