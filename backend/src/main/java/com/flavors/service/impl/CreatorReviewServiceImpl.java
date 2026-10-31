package com.flavors.service.impl;

import com.flavors.dto.CreatorReviewResponse;
import com.flavors.dto.PendingCreatorResponse;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import com.flavors.enums.VerificationStatus;
import com.flavors.exception.CreatorNotFoundException;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.mapper.CreatorReviewMapper;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.CreatorReviewService;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorReviewServiceImpl implements CreatorReviewService {

    private final CreatorProfileRepository creators;
    private final SocialAccountRepository socialAccounts;
    private final AppUserRepository users;
    private final CreatorReviewMapper mapper;
    private final AuditLogger auditLogger;

    public CreatorReviewServiceImpl(CreatorProfileRepository creators, SocialAccountRepository socialAccounts,
                                    AppUserRepository users, CreatorReviewMapper mapper, AuditLogger auditLogger) {
        this.creators = creators;
        this.socialAccounts = socialAccounts;
        this.users = users;
        this.mapper = mapper;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingCreatorResponse> listPending() {
        return creators.findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus.PENDING_APPROVAL).stream()
                .map(mapper::toPending)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorReviewResponse get(long creatorId) {
        return toResponse(find(creatorId));
    }

    @Override
    @Transactional
    public CreatorReviewResponse approve(long adminId, long creatorId) {
        CreatorProfile creator = find(creatorId);
        requirePending(creator);
        if (CreatorReviewMapper.needsReconnection(socialAccounts.findByCreatorId(creatorId))) {
            throw new ReviewNotAllowedException(
                    "Needs reconnection: approval is blocked until the creator reconnects her social account.");
        }

        // The id at the end keeps the public address unique (creator_profile.storefront_slug is UNIQUE).
        creator.approve(adminId, slugFor(creator.getDisplayName(), creatorId), Instant.now());
        creators.save(creator);
        auditLogger.record("CREATOR_APPROVED", "app_user", creatorId, Map.of("adminId", adminId));
        return toResponse(creator);
    }

    @Override
    @Transactional
    public CreatorReviewResponse reject(long adminId, long creatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationFailedException("reason", "A reason is required to reject a profile.");
        }
        CreatorProfile creator = find(creatorId);
        requirePending(creator);

        creator.reject(adminId, reason.trim(), Instant.now());
        creators.save(creator);
        auditLogger.record("CREATOR_REJECTED", "app_user", creatorId, Map.of("adminId", adminId));
        return toResponse(creator);
    }

    private CreatorProfile find(long creatorId) {
        return creators.findById(creatorId).orElseThrow(() -> new CreatorNotFoundException(creatorId));
    }

    private static void requirePending(CreatorProfile creator) {
        if (creator.getVerificationStatus() != VerificationStatus.PENDING_APPROVAL) {
            throw new ReviewNotAllowedException("This profile was already reviewed.");
        }
    }

    private CreatorReviewResponse toResponse(CreatorProfile creator) {
        String email = users.findById(creator.getUserId()).orElseThrow().getEmail();
        List<SocialAccount> accounts = socialAccounts.findByCreatorId(creator.getUserId());
        return mapper.toReview(creator, email, accounts);
    }

    static String slugFor(String displayName, long creatorId) {
        String ascii = Normalizer.normalize(displayName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String words = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (words.length() > 60) {
            words = words.substring(0, 60).replaceAll("-+$", "");
        }
        return words.isEmpty() ? "creator-" + creatorId : words + "-" + creatorId;
    }
}
