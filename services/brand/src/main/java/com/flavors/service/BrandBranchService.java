package com.flavors.service;

import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.dto.BranchResponse;
import java.util.List;

public interface BrandBranchService {

    List<BranchResponse> list(long brandId);

    BranchResponse add(long brandId, BranchRegistrationRequest request);
}
