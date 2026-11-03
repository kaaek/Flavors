package com.flavors.service.impl;

import com.flavors.dto.CreatorRegistrationRequest;
import com.flavors.dto.CreatorRegistrationResponse;
import com.flavors.dto.SocialAccountRegistrationRequest;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import com.flavors.enums.Role;
import com.flavors.exception.DuplicateEmailException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.CreatorRegistrationService;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorRegistrationServiceImpl implements CreatorRegistrationService {

    private final AppUserRepository users;
    private final CreatorProfileRepository creators;
    private final SocialAccountRepository socialAccounts;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public CreatorRegistrationServiceImpl(AppUserRepository users, CreatorProfileRepository creators,
                                          SocialAccountRepository socialAccounts, PasswordEncoder passwordEncoder,
                                          AuditLogger auditLogger) {
        this.users = users;
        this.creators = creators;
        this.socialAccounts = socialAccounts;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional
    public CreatorRegistrationResponse register(CreatorRegistrationRequest request) {
        // These two rules span several fields, so the annotations on the request can't check them (EX-F19.1.1, EX-F19.1.3).
        if (request.priceMinCents() > request.priceMaxCents()) {
            throw new ValidationFailedException("priceMinCents", "Minimum price can't be above the maximum price.");
        }
        if (request.socialAccounts() == null || request.socialAccounts().isEmpty()) {
            throw new ValidationFailedException("socialAccounts", "Connect at least one social account.");
        }

        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateEmailException();
        }

        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password()), Role.CREATOR));
        } catch (DataIntegrityViolationException exception) {
            // The case-insensitive unique email index also protects concurrent registrations.
            throw new DuplicateEmailException();
        }

        // A new profile starts as PENDING_APPROVAL: not verified, not searchable, no storefront (FR-F19.4).
        CreatorProfile profile = new CreatorProfile(user.getId(), request.name().trim(),
                request.foodCategory().trim(), request.location().trim(),
                request.priceMinCents(), request.priceMaxCents());
        if (request.bio() != null && !request.bio().isBlank()) {
            profile.updateDetails(request.bio().trim(), profile.getFoodCategory(), profile.getLocation(),
                    profile.getPriceMinCents(), profile.getPriceMaxCents());
        }
        // Flushed now because social_account has a foreign key to this row.
        creators.saveAndFlush(profile);

        List<SocialAccount> accounts = request.socialAccounts().stream()
                .map(account -> toEntity(user.getId(), account))
                .toList();
        try {
            socialAccounts.saveAllAndFlush(accounts);
        } catch (DataIntegrityViolationException exception) {
            // One platform account can't belong to two creators (unique platform + external id in V2).
            throw new ValidationFailedException("socialAccounts",
                    "This social account is already connected to another creator.");
        }

        auditLogger.record("CREATOR_REGISTERED", "app_user", user.getId(),
                Map.of("socialAccountCount", accounts.size()));

        return new CreatorRegistrationResponse(user.getId(), user.getEmail(), user.getRole(),
                profile.getVerificationStatus());
    }

    private static SocialAccount toEntity(Long creatorId, SocialAccountRegistrationRequest account) {
        return new SocialAccount(creatorId, account.platform().trim().toLowerCase(), account.externalId().trim());
    }
}
