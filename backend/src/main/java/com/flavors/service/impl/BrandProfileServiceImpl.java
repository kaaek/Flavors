package com.flavors.service.impl;

import com.flavors.dto.BrandProfileResponse;
import com.flavors.dto.UpdateBrandProfileRequest;
import com.flavors.entity.Brand;
import com.flavors.repository.BrandRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.BrandProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandProfileServiceImpl implements BrandProfileService {

    private final BrandRepository brands;
    private final AuditLogger auditLogger;

    public BrandProfileServiceImpl(BrandRepository brands, AuditLogger auditLogger) {
        this.brands = brands;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional(readOnly = true)
    public BrandProfileResponse get(long brandId) {
        return toResponse(load(brandId));
    }

    @Override
    @Transactional
    public BrandProfileResponse update(long brandId, UpdateBrandProfileRequest request) {
        Brand brand = load(brandId);
        brand.updateProfile(request.cuisine(), request.description());
        auditLogger.record("BRAND_PROFILE_UPDATED", "brand", brandId);
        return toResponse(brand);
    }

    // A BRAND-role user always has a brand row (registration creates both), so a miss is a bug.
    private Brand load(long brandId) {
        return brands.findById(brandId)
                .orElseThrow(() -> new IllegalStateException("No brand row for user " + brandId));
    }

    private static BrandProfileResponse toResponse(Brand brand) {
        return new BrandProfileResponse(brand.getBusinessName(), brand.getCuisine(), brand.getDescription());
    }
}
