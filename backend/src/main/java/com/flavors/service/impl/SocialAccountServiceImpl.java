package com.flavors.service.impl;

import com.flavors.dto.SocialAccountResponse;
import com.flavors.entity.SocialAccount;
import com.flavors.entity.SocialPostMetric;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.CreatorProfileMissingException;
import com.flavors.exception.SocialAccountNotFoundException;
import com.flavors.exception.SocialAccountTakenException;
import com.flavors.exception.SocialConnectionUnavailableException;
import com.flavors.exception.SocialPlatformException;
import com.flavors.exception.SocialTokenInvalidException;
import com.flavors.mapper.SocialAccountMapper;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.repository.SocialAccountTokenRepository;
import com.flavors.repository.SocialPostMetricRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.InstagramClient;
import com.flavors.service.SocialAccountService;
import com.flavors.util.TokenCipher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SocialAccountServiceImpl implements SocialAccountService {

    /** The largest value the engagement_rate column (numeric(7, 2)) can hold. */
    private static final BigDecimal MAX_RATE = new BigDecimal("99999.99");

    private final SocialAccountRepository accounts;
    private final SocialAccountTokenRepository tokens;
    private final SocialPostMetricRepository postMetrics;
    private final CreatorProfileRepository profiles;
    private final InstagramClient instagram;
    private final TokenCipher cipher;
    private final SocialAccountMapper mapper;
    private final AuditLogger auditLogger;

    SocialAccountServiceImpl(SocialAccountRepository accounts, SocialAccountTokenRepository tokens,
                             SocialPostMetricRepository postMetrics, CreatorProfileRepository profiles,
                             InstagramClient instagram, TokenCipher cipher, SocialAccountMapper mapper,
                             AuditLogger auditLogger) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.postMetrics = postMetrics;
        this.profiles = profiles;
        this.instagram = instagram;
        this.cipher = cipher;
        this.mapper = mapper;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional(readOnly = true)
    public String instagramAuthorizationUrl(long creatorId, String state) {
        requireReady();
        requireProfile(creatorId);
        return instagram.authorizationUrl(state);
    }

    @Override
    public SocialAccountResponse connectInstagram(long creatorId, String code) {
        requireReady();
        requireProfile(creatorId);

        // Both calls to Instagram happen before anything is saved, so a failure saves nothing (EX-F19.4.1).
        String token = instagram.exchangeCode(code);
        InstagramClient.Snapshot snapshot = instagram.fetchSnapshot(token);

        Optional<SocialAccount> existing =
                accounts.findByPlatformAndExternalId(InstagramClient.PLATFORM, snapshot.externalId());
        if (existing.isPresent() && !existing.get().getCreatorId().equals(creatorId)) {
            throw new SocialAccountTakenException();
        }
        // Flushed now: the token is written with plain SQL, so the row must already exist.
        SocialAccount account = existing.orElseGet(() -> accounts.saveAndFlush(
                new SocialAccount(creatorId, InstagramClient.PLATFORM, snapshot.externalId())));

        storeFigures(account, snapshot, Instant.now());
        tokens.store(account.getId(), cipher.encrypt(token));

        auditLogger.record(existing.isPresent() ? "SOCIAL_ACCOUNT_RECONNECTED" : "SOCIAL_ACCOUNT_CONNECTED",
                "social_account", account.getId(),
                Map.of("platform", InstagramClient.PLATFORM, "followers", snapshot.followers()));
        return mapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialAccountResponse> myAccounts(long creatorId) {
        return accounts.findByCreatorId(creatorId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public void disconnect(long creatorId, long accountId) {
        SocialAccount account = accounts.findByIdAndCreatorId(accountId, creatorId)
                .orElseThrow(SocialAccountNotFoundException::new);
        account.markNeedsReconnection();
        tokens.clear(account.getId());
        auditLogger.record("SOCIAL_ACCOUNT_DISCONNECTED", "social_account", account.getId());
    }

    @Override
    public boolean refresh(long accountId) {
        SocialAccount account = accounts.findById(accountId).orElseThrow(SocialAccountNotFoundException::new);

        Optional<byte[]> stored = tokens.find(accountId);
        if (stored.isEmpty() || !cipher.isConfigured()) {
            flag(account, "no token");
            return false;
        }
        String token;
        try {
            token = cipher.decrypt(stored.get());
        } catch (IllegalStateException e) {
            // Wrong key or changed bytes: the token can't be used, so she must reconnect.
            flag(account, "token unreadable");
            return false;
        }
        try {
            storeFigures(account, instagram.fetchSnapshot(token), Instant.now());
            return true;
        } catch (SocialTokenInvalidException e) {
            flag(account, "token revoked or expired");
            return false;
        } catch (SocialPlatformException e) {
            flag(account, "pull failed");
            return false;
        }
    }

    /**
     * Average interactions (likes + comments + shares) per recent post, as a percentage of followers, rounded
     * to 2 decimals. Null when it can't be worked out (no followers or no posts yet).
     */
    static BigDecimal engagementRate(long followers, List<InstagramClient.PostFigures> posts) {
        if (followers <= 0 || posts.isEmpty()) {
            return null;
        }
        long interactions = posts.stream().mapToLong(p -> p.likes() + p.comments() + p.shares()).sum();
        BigDecimal rate = BigDecimal.valueOf(interactions)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(followers).multiply(BigDecimal.valueOf(posts.size())), 2, RoundingMode.HALF_UP);
        return rate.min(MAX_RATE);
    }

    /** The account's figures, and one row per post, overwritten by each pull (FR-F19.15, FR-F1.2). */
    private void storeFigures(SocialAccount account, InstagramClient.Snapshot snapshot, Instant pulledAt) {
        account.recordPull(snapshot.followers(), engagementRate(snapshot.followers(), snapshot.posts()), pulledAt);
        for (InstagramClient.PostFigures post : snapshot.posts()) {
            SocialPostMetric metric = postMetrics
                    .findBySocialAccountIdAndExternalPostId(account.getId(), post.externalPostId())
                    .orElseGet(() -> new SocialPostMetric(account.getId(), post.externalPostId()));
            metric.record(post.views(), post.likes(), post.comments(), post.shares(), pulledAt);
            postMetrics.save(metric);
        }
    }

    /** Keeps the last figures and their time; audited once, when the account changes to 'Needs reconnection'. */
    private void flag(SocialAccount account, String reason) {
        if (account.getStatus() != SocialAccountStatus.NEEDS_RECONNECTION) {
            account.markNeedsReconnection();
            auditLogger.record("SOCIAL_ACCOUNT_NEEDS_RECONNECTION", "social_account", account.getId(),
                    Map.of("reason", reason));
        }
    }

    private void requireReady() {
        if (!instagram.isConfigured() || !cipher.isConfigured()) {
            throw new SocialConnectionUnavailableException();
        }
    }

    private void requireProfile(long creatorId) {
        if (!profiles.existsById(creatorId)) {
            throw new CreatorProfileMissingException();
        }
    }
}
