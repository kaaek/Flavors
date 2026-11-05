package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.flavors.TestcontainersConfiguration;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.SocialTokenInvalidException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.InstagramClient;
import com.flavors.service.SocialAccountService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The daily refresh (EEC-37) against a real Postgres, with Instagram replaced by a stand-in. Covers TC-F1.3.1 and
 * TC-F1.3.2 from the Jira story, plus a failing account not stopping the others and the one-instance lock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flavors.token-encryption-key=" + SocialAccountServiceImplIT.TEST_KEY)
class SocialRefreshJobIT {

    @MockitoBean
    InstagramClient instagram;

    @Autowired
    SocialRefreshJob job;

    @Autowired
    SocialAccountService service;

    @Autowired
    SocialAccountRepository accounts;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository profiles;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void startWithoutOtherAccounts() {
        // The job pulls every connected account, so accounts left by other test classes would be pulled too.
        jdbc.update("DELETE FROM social_post_metric");
        jdbc.update("DELETE FROM social_account");
        given(instagram.isConfigured()).willReturn(true);
    }

    // ---- TC-F1.3.1 ----

    @Test
    void theDailyRunUpdatesTheFiguresAndThePullTimeOfAConnectedAccount() {
        String externalId = connect("token-1", 12000);
        SocialAccount before = onlyAccount();

        given(instagram.fetchSnapshot(eq("token-1"), anyString())).willReturn(snapshot(externalId, 15000));
        SocialRefreshJob.Result result = job.runOnce();

        SocialAccount after = onlyAccount();
        assertThat(result).isEqualTo(new SocialRefreshJob.Result(false, 1, 0, 0));
        assertThat(after.getFollowers()).isEqualTo(15000);
        assertThat(after.getEngagementRate()).isEqualByComparingTo("2.40");
        assertThat(after.getLastPulledAt()).isAfter(before.getLastPulledAt());
        assertThat(after.getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
    }

    // ---- TC-F1.3.2 ----

    @Test
    void aDisconnectedAccountKeepsItsOldFiguresAndTimeAndShowsNeedsReconnection() {
        long creator = newCreator();
        String externalId = "ig-" + UUID.randomUUID();
        given(instagram.exchangeCode("code")).willReturn("token-1");
        given(instagram.assignedAccounts("token-1")).willReturn(List.of(new InstagramClient.AccountRef(externalId, "lara.eats")));
        given(instagram.fetchSnapshot(eq("token-1"), anyString())).willReturn(snapshot(externalId, 12000));
        long id = service.connectInstagram(creator, "code").id();
        SocialAccount before = accounts.findById(id).orElseThrow();
        service.disconnect(creator, id);

        SocialRefreshJob.Result result = job.runOnce();

        SocialAccount after = accounts.findById(id).orElseThrow();
        assertThat(result).isEqualTo(new SocialRefreshJob.Result(false, 0, 0, 0));
        assertThat(after.getStatus()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION);
        assertThat(after.getFollowers()).isEqualTo(12000);
        assertThat(after.getLastPulledAt()).isEqualTo(before.getLastPulledAt());
        // The job didn't ask Instagram about it again (only the connect pulled).
        then(instagram).should(times(1)).fetchSnapshot(eq("token-1"), anyString());
    }

    // ---- EX-F1.3.1 and one failure not stopping the rest ----

    @Test
    void aFailedPullFlagsThatAccountWhileTheOthersStillRefresh() {
        String revokedId = connect("token-revoked", 12000);
        String goodId = connect("token-good", 12000);
        SocialAccount revokedBefore = accounts.findAll().stream()
                .filter(a -> a.getExternalId().equals(revokedId)).findFirst().orElseThrow();
        given(instagram.fetchSnapshot(eq("token-revoked"), anyString())).willThrow(new SocialTokenInvalidException());
        given(instagram.fetchSnapshot(eq("token-good"), anyString())).willReturn(snapshot(goodId, 20000));

        SocialRefreshJob.Result result = job.runOnce();

        assertThat(result).isEqualTo(new SocialRefreshJob.Result(false, 1, 1, 0));
        SocialAccount revoked = accounts.findById(revokedBefore.getId()).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION);
        assertThat(revoked.getFollowers()).isEqualTo(12000);
        assertThat(revoked.getLastPulledAt()).isEqualTo(revokedBefore.getLastPulledAt());
        SocialAccount good = accounts.findAll().stream()
                .filter(a -> a.getExternalId().equals(goodId)).findFirst().orElseThrow();
        assertThat(good.getFollowers()).isEqualTo(20000);
    }

    @Test
    void anUnexpectedErrorInOneAccountDoesNotStopTheNext() {
        String brokenId = connect("token-broken", 12000);
        String goodId = connect("token-good", 12000);
        given(instagram.fetchSnapshot(eq("token-broken"), anyString())).willThrow(new IllegalStateException("unexpected"));
        given(instagram.fetchSnapshot(eq("token-good"), anyString())).willReturn(snapshot(goodId, 20000));

        SocialRefreshJob.Result result = job.runOnce();

        assertThat(result).isEqualTo(new SocialRefreshJob.Result(false, 1, 0, 1));
        assertThat(accounts.findAll().stream().filter(a -> a.getExternalId().equals(goodId)).findFirst().orElseThrow()
                .getFollowers()).isEqualTo(20000);
        assertThat(accounts.findAll().stream().filter(a -> a.getExternalId().equals(brokenId)).findFirst().orElseThrow()
                .getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
    }

    // ---- Where Instagram is not set up, nobody is flagged ----

    @Test
    void withoutTheInstagramSettingsNoAccountIsTouchedOrFlagged() {
        connect("token-1", 12000);
        given(instagram.isConfigured()).willReturn(false);

        assertThat(job.runOnce().skipped()).isTrue();

        assertThat(onlyAccount().getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
    }

    // ---- One instance at a time ----

    @Test
    void theScheduledRunDoesNothingWhileAnotherInstanceHoldsTheLock() throws SQLException {
        String externalId = connect("token-1", 12000);
        given(instagram.fetchSnapshot(eq("token-1"), anyString())).willReturn(snapshot(externalId, 15000));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, SocialRefreshJob.LOCK_KEY);
                lock.execute();
            }

            job.runDaily();

            assertThat(onlyAccount().getFollowers()).isEqualTo(12000);
            other.rollback();
        }

        // Once the other instance is done, the next run goes ahead.
        job.runDaily();
        assertThat(onlyAccount().getFollowers()).isEqualTo(15000);
    }

    // ---- Helpers ----

    private long newCreator() {
        String email = UUID.randomUUID() + "@test.local";
        long id = users.save(new AppUser(email, "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        return id;
    }

    /** Connects a new creator's Instagram account with this token; returns the account's id on Instagram. */
    private String connect(String token, long followers) {
        long creator = newCreator();
        String externalId = "ig-" + UUID.randomUUID();
        String code = "code-" + token;
        given(instagram.exchangeCode(code)).willReturn(token);
        given(instagram.assignedAccounts(token)).willReturn(List.of(new InstagramClient.AccountRef(externalId, "lara.eats")));
        given(instagram.fetchSnapshot(eq(token), anyString())).willReturn(snapshot(externalId, followers));
        service.connectInstagram(creator, code);
        return externalId;
    }

    private SocialAccount onlyAccount() {
        List<SocialAccount> all = accounts.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    /** Two posts with 480 and 240 interactions: 360 per post. */
    private static InstagramClient.Snapshot snapshot(String externalId, long followers) {
        return new InstagramClient.Snapshot(externalId, "lara.eats", followers, List.of(
                new InstagramClient.PostFigures("post-1", 5000, 400, 50, 30),
                new InstagramClient.PostFigures("post-2", 3000, 200, 30, 10)));
    }
}
