package com.flavors.service.impl;

import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.dto.BranchResponse;
import com.flavors.entity.Branch;
import com.flavors.repository.BranchRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.BrandBranchService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandBranchServiceImpl implements BrandBranchService {

    private final BranchRepository branches;
    private final AuditLogger auditLogger;

    public BrandBranchServiceImpl(BranchRepository branches, AuditLogger auditLogger) {
        this.branches = branches;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> list(long brandId) {
        return branches.findByBrandIdOrderByIdAsc(brandId).stream()
                .map(BrandBranchServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BranchResponse add(long brandId, BranchRegistrationRequest request) {
        Branch branch = branches.save(new Branch(brandId, request.name().trim(), request.location().trim()));
        auditLogger.record("BRANCH_ADDED", "branch", branch.getId());
        return toResponse(branch);
    }

    private static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getLocation());
    }
}
