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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate tx;

    SocialAccountServiceImpl(SocialAccountRepository accounts, SocialAccountTokenRepository tokens,
                             SocialPostMetricRepository postMetrics, CreatorProfileRepository profiles,
                             InstagramClient instagram, TokenCipher cipher, SocialAccountMapper mapper,
                             AuditLogger auditLogger, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.postMetrics = postMetrics;
        this.profiles = profiles;
        this.instagram = instagram;
        this.cipher = cipher;
        this.mapper = mapper;
        this.auditLogger = auditLogger;
        this.tx = new TransactionTemplate(transactionManager);
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
        // Flushed now: the token is written with plain SQL, so the row must already exist. A reconnect takes the
        // account lock, so a pull that started with the old token can't write its result over the new connection.
        SocialAccount account = existing.isPresent()
                ? accounts.lockById(existing.get().getId()).orElseThrow(SocialAccountNotFoundException::new)
                : accounts.saveAndFlush(new SocialAccount(creatorId, InstagramClient.PLATFORM, snapshot.externalId()));

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
        SocialAccount owned = accounts.findByIdAndCreatorId(accountId, creatorId)
                .orElseThrow(SocialAccountNotFoundException::new);
        SocialAccount account = accounts.lockById(owned.getId()).orElseThrow(SocialAccountNotFoundException::new);
        account.markNeedsReconnection();
        tokens.clear(account.getId());
        auditLogger.record("SOCIAL_ACCOUNT_DISCONNECTED", "social_account", account.getId());
    }

    /**
     * Not one big transaction: a pull makes many slow calls to Instagram, and a transaction would hold a database
     * connection for all of them. So it is read (short transaction), pull (none), write (short transaction).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean refresh(long accountId) {
        Optional<byte[]> stored = tx.execute(status -> {
            if (!accounts.existsById(accountId)) {
                throw new SocialAccountNotFoundException();
            }
            return tokens.find(accountId);
        });

        if (stored.isEmpty() || !cipher.isConfigured()) {
            return flagIfUnchanged(accountId, stored, "no token");
        }
        String token;
        try {
            token = cipher.decrypt(stored.get());
        } catch (IllegalStateException e) {
            // Wrong key or changed bytes: the token can't be used, so she must reconnect.
            return flagIfUnchanged(accountId, stored, "token unreadable");
        }
        // A long-lived token lasts 60 days. Renewing on every pull keeps it alive without storing an expiry date.
        // Renewing is best effort: Instagram refuses it for a token under 24 hours old, and the old token still
        // works, so only a dead token stops the pull.
        String renewed = null;
        try {
            renewed = instagram.renewToken(token);
        } catch (SocialTokenInvalidException e) {
            return flagIfUnchanged(accountId, stored, "token revoked or expired");
        } catch (SocialPlatformException e) {
            // keep the current token
        }
        String activeToken = renewed != null ? renewed : token;

        InstagramClient.Snapshot snapshot;
        try {
            snapshot = instagram.fetchSnapshot(activeToken);
        } catch (SocialTokenInvalidException e) {
            return flagIfUnchanged(accountId, stored, "token revoked or expired");
        } catch (SocialPlatformException e) {
            return flagIfUnchanged(accountId, stored, "pull failed");
        }
        String newToken = renewed;
        return writeIfUnchanged(accountId, stored, account -> {
            storeFigures(account, snapshot, Instant.now());
            if (newToken != null) {
                tokens.store(accountId, cipher.encrypt(newToken));
            }
        });
    }

    /** Records a failed pull, unless the token changed while the pull ran. Always returns false. */
    private boolean flagIfUnchanged(long accountId, Optional<byte[]> tokenUsed, String reason) {
        writeIfUnchanged(accountId, tokenUsed, account -> flag(account, reason));
        return false;
    }

    /**
     * The pull ran without a transaction, so the creator may have disconnected (token cleared) or reconnected
     * (new token) meanwhile. Then this result is stale: writing it would undo her disconnect, or flag a fresh
     * connection. Returns true only if the change was written.
     */
    private boolean writeIfUnchanged(long accountId, Optional<byte[]> tokenUsed, Consumer<SocialAccount> change) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            // Locked first: a disconnect that is committing right now finishes before the token is compared.
            SocialAccount account = accounts.lockById(accountId).orElseThrow(SocialAccountNotFoundException::new);
            Optional<byte[]> current = tokens.find(accountId);
            boolean same = current.isPresent() == tokenUsed.isPresent()
                    && (current.isEmpty() || Arrays.equals(current.get(), tokenUsed.get()));
            if (!same) {
                return false;
            }
            change.accept(account);
            return true;
        }));
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
