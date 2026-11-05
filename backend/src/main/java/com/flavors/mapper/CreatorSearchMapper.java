package com.flavors.mapper;

import com.flavors.dto.CreatorSearchResult;
import com.flavors.repository.CreatorSearchRow;
import org.springframework.stereotype.Component;

@Component
public class CreatorSearchMapper {

    // roi and redemptions are always null for now: they come from campaigns, which don't exist yet.
    public CreatorSearchResult toResult(CreatorSearchRow row) {
        return new CreatorSearchResult(row.creatorId(), row.displayName(), row.foodCategory(), row.location(),
                row.platform(), row.followers(), row.engagementRate(), row.priceMinCents(), row.priceMaxCents(),
                null, null, row.storefrontSlug(), row.saved());
    }
}
