package com.flavors.service;

import com.flavors.dto.BrandProfileResponse;
import com.flavors.dto.UpdateBrandProfileRequest;

public interface BrandProfileService {

    BrandProfileResponse get(long brandId);

    BrandProfileResponse update(long brandId, UpdateBrandProfileRequest request);
}
