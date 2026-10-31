package com.flavors.service.impl;

import com.flavors.dto.BrandRegistrationRequest;
import com.flavors.dto.BrandRegistrationResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.Brand;
import com.flavors.entity.Branch;
import com.flavors.enums.Role;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.BrandRepository;
import com.flavors.repository.BranchRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.BrandRegistrationService;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandRegistrationServiceImpl implements BrandRegistrationService {

    private final AppUserRepository users;
    private final BrandRepository brands;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public BrandRegistrationServiceImpl(AppUserRepository users, BrandRepository brands,
                                        BranchRepository branches, PasswordEncoder passwordEncoder,
                                        AuditLogger auditLogger) {
        this.users = users;
        this.brands = brands;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional
    public BrandRegistrationResponse register(BrandRegistrationRequest request) {
        if (request.branches() == null || request.branches().isEmpty()) {
            throw new IllegalArgumentException("At least one branch is required.");
        }

        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateEmailException();
        }

        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password()), Role.BRAND));
        } catch (DataIntegrityViolationException exception) {
            // The case-insensitive unique email index also protects concurrent registrations.
            throw new DuplicateEmailException();
        }

        brands.save(new Brand(user.getId(), request.businessName().trim(),
                request.contactPerson().trim(), request.phone().trim()));
        branches.saveAll(request.branches().stream()
                .map(branch -> new Branch(user.getId(), branch.name().trim(), branch.location().trim()))
                .toList());
        auditLogger.record("BRAND_REGISTERED", "app_user", user.getId(),
                Map.of("branchCount", request.branches().size()));

        return new BrandRegistrationResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
