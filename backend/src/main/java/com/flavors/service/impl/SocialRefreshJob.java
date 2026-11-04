package com.flavors.service.impl;

import com.flavors.enums.SocialAccountStatus;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.InstagramClient;
import com.flavors.service.SocialAccountService;
import com.flavors.util.TokenCipher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The daily refresh of every connected social account (EEC-37, UC-F1.3, FR-F1.2). It only decides which
 * accounts to pull and keeps one failure from stopping the rest; each pull, and what a failed one does
 * ('Needs reconnection', last figures kept), lives in {@link SocialAccountService#refresh(long)}.
 *
 * <p>The time is {@code FLAVORS_SOCIAL_REFRESH_CRON} (default 03:00) in {@code FLAVORS_SOCIAL_REFRESH_ZONE}
 * (default Asia/Beirut); {@code FLAVORS_SOCIAL_REFRESH_ENABLED=false} turns the job off.
 */
@Component
@ConditionalOnProperty(name = "flavors.social.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class SocialRefreshJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocialRefreshJob.class);

    /** Any constant; it only has to differ from other advisory locks the app may take. */
    static final long LOCK_KEY = 37_001L;

    private final SocialAccountService service;
    private final SocialAccountRepository accounts;
    private final InstagramClient instagram;
    private final TokenCipher cipher;
    private final DataSource dataSource;

    public SocialRefreshJob(SocialAccountService service, SocialAccountRepository accounts, InstagramClient instagram,
                            TokenCipher cipher, DataSource dataSource) {
        this.service = service;
        this.accounts = accounts;
        this.instagram = instagram;
        this.cipher = cipher;
        this.dataSource = dataSource;
    }

    /** What one run did. {@code skipped} is true when it didn't run at all. */
    public record Result(boolean skipped, int refreshed, int needReconnection, int errors) {
        static Result skippedRun() {
            return new Result(true, 0, 0, 0);
        }
    }

    @Scheduled(cron = "${flavors.social.refresh-cron:0 0 3 * * *}",
            zone = "${flavors.social.refresh-zone:Asia/Beirut}")
    public void runDaily() {
        // Railway may run more than one instance. A Postgres lock lets exactly one of them do the run; the lock
        // is released when this transaction ends, even if the app dies mid-run.
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!tryLock(connection)) {
                    LOGGER.info("Daily social refresh skipped: another instance is running it.");
                    return;
                }
                runOnce();
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOGGER.error("Daily social refresh could not take its lock.", e);
        }
    }

    /** One pass over every connected account, without the lock (tests call this directly). */
    public Result runOnce() {
        // Without the Meta settings or the key every pull would fail and flag every creator as 'Needs reconnection'.
        if (!instagram.isConfigured() || !cipher.isConfigured()) {
            LOGGER.info("Daily social refresh skipped: Instagram is not set up in this environment.");
            return Result.skippedRun();
        }
        int refreshed = 0;
        int needReconnection = 0;
        int errors = 0;
        for (long id : accounts.findIdsByStatus(SocialAccountStatus.CONNECTED)) {
            try {
                if (service.refresh(id)) {
                    refreshed++;
                } else {
                    needReconnection++;
                }
            } catch (RuntimeException e) {
                // One account's unexpected failure must not stop the others. Only the id is logged: an Instagram
                // URL or token must never reach the log.
                errors++;
                LOGGER.error("Daily social refresh failed for account {} ({}).", id, e.getClass().getSimpleName());
            }
        }
        LOGGER.info("Daily social refresh done: {} refreshed, {} need reconnection, {} errors.",
                refreshed, needReconnection, errors);
        return new Result(false, refreshed, needReconnection, errors);
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean(1);
            }
        }
    }
}
