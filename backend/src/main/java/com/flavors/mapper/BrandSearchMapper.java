package com.flavors.mapper;

import com.flavors.dto.BrandSearchResult;
import com.flavors.repository.BrandSearchRow;
import org.springframework.stereotype.Component;

@Component
public class BrandSearchMapper {

    // The row has no phone or contact person on purpose, so they can't leak from here.
    public BrandSearchResult toResult(BrandSearchRow row) {
        return new BrandSearchResult(row.brandId(), row.businessName(), row.cuisine(), row.description(),
                row.branchLocations());
    }
}
