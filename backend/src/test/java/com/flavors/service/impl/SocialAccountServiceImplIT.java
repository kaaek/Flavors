package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.SocialAccountResponse;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.entity.SocialAccount;
import com.flavors.entity.SocialPostMetric;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.exception.CreatorProfileMissingException;
import com.flavors.exception.SocialAccountNotFoundException;
import com.flavors.exception.SocialAccountTakenException;
import com.flavors.exception.SocialConnectionUnavailableException;
import com.flavors.exception.SocialPlatformException;
import com.flavors.exception.SocialTokenInvalidException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.repository.SocialAccountTokenRepository;
import com.flavors.repository.SocialPostMetricRepository;
import com.flavors.service.InstagramClient;
import com.flavors.service.SocialAccountService;
import com.flavors.util.TokenCipher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Connecting a social account (EEC-33) against a real Postgres. Instagram is replaced by a stand-in (the
 * InstagramClient mock), so no test ever calls Instagram. Covers TC-F19.4.1 to TC-F19.4.3 from the Jira
 * story, plus reconnecting, disconnecting and who owns an account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "flavors.token-encryption-key=" + SocialAccountServiceImplIT.TEST_KEY)
class SocialAccountServiceImplIT {

    /** A throwaway key, used only by these tests. */
    static final String TEST_KEY = "P5YHKVMQexj67PaCeSKT4mhXMV6LG5vCIPb7QdnH2eI=";

    @MockitoBean
    InstagramClient instagram;

    @Autowired
    SocialAccountService service;

    @Autowired
    SocialAccountRepository accounts;

    @Autowired
    SocialAccountTokenRepository tokens;

    @Autowired
    SocialPostMetricRepository postMetrics;

    @Autowired
    AppUserRepository users;

    @Autowired
    CreatorProfileRepository profiles;

    @Autowired
    TokenCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void instagramIsSetUp() {
        given(instagram.isConfigured()).willReturn(true);
    }

    // ---- TC-F19.4.1 ----

    @Test
    void connectingSavesTheAccountWithItsFirstFiguresAndTheTimeOfThePull() {
        long lara = newCreator();
        String externalId = newExternalId();
        instagramAccepts("code-1", "token-1", externalId);

        SocialAccountResponse response = service.connectInstagram(lara, "code-1");

        SocialAccount saved = accounts.findById(response.id()).orElseThrow();
        assertThat(saved.getCreatorId()).isEqualTo(lara);
        assertThat(saved.getPlatform()).isEqualTo("instagram");
        assertThat(saved.getExternalId()).isEqualTo(externalId);
        assertThat(saved.getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
        assertThat(saved.getFollowers()).isEqualTo(12000);
        assertThat(saved.getEngagementRate()).isEqualByComparingTo("3.00");
        assertThat(saved.getLastPulledAt()).isNotNull();

        // Per-post figures (FR-F1.2), pulled at the same time as the account's.
        List<SocialPostMetric> posts = postMetrics.findBySocialAccountId(saved.getId());
        assertThat(posts).extracting(SocialPostMetric::getExternalPostId).containsExactlyInAnyOrder("post-1", "post-2");
        SocialPostMetric first = posts.stream().filter(p -> p.getExternalPostId().equals("post-1")).findFirst().orElseThrow();
        assertThat(first.getViews()).isEqualTo(5000);
        assertThat(first.getLikes()).isEqualTo(400);
        assertThat(first.getComments()).isEqualTo(50);
        assertThat(first.getShares()).isEqualTo(30);
        assertThat(first.getPulledAt()).isEqualTo(saved.getLastPulledAt());

        // The token is stored encrypted, never as plain text.
        byte[] stored = tokens.find(saved.getId()).orElseThrow();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("token-1");
        assertThat(cipher.decrypt(stored)).isEqualTo("token-1");

        assertThat(auditCount("SOCIAL_ACCOUNT_CONNECTED", saved.getId())).isEqualTo(1);
    }

    // ---- TC-F19.4.2 (EX-F19.4.1) ----

    @Test
    void aFailedConnectionSavesNothingAndSheCanTryAgain() {
        long lara = newCreator();

        // Instagram refuses the code.
        given(instagram.exchangeCode("refused-code")).willThrow(new SocialPlatformException("refused"));
        assertThatThrownBy(() -> service.connectInstagram(lara, "refused-code"))
                .isInstanceOf(SocialPlatformException.class);
        assertThat(accounts.findByCreatorId(lara)).isEmpty();

        // The code works but reading the account fails.
        given(instagram.exchangeCode("code-2")).willReturn("token-2");
        given(instagram.fetchSnapshot("token-2")).willThrow(new SocialPlatformException("down"));
        assertThatThrownBy(() -> service.connectInstagram(lara, "code-2"))
                .isInstanceOf(SocialPlatformException.class);
        assertThat(accounts.findByCreatorId(lara)).isEmpty();

        // She tries again and it works.
        instagramAccepts("code-3", "token-3", newExternalId());
        service.connectInstagram(lara, "code-3");
        assertThat(accounts.findByCreatorId(lara)).hasSize(1);
    }

    // ---- TC-F19.4.3 ----

    @Test
    void aRevokedTokenShowsNeedsReconnectionAndKeepsTheLastFigures() {
        long lara = newCreator();
        instagramAccepts("code-1", "token-1", newExternalId());
        long id = service.connectInstagram(lara, "code-1").id();
        SocialAccount before = accounts.findById(id).orElseThrow();

        given(instagram.fetchSnapshot("token-1")).willThrow(new SocialTokenInvalidException());

        assertThat(service.refresh(id)).isFalse();

        SocialAccount after = accounts.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION);
        assertThat(after.getFollowers()).isEqualTo(12000);
        assertThat(after.getEngagementRate()).isEqualByComparingTo("3.00");
        assertThat(after.getLastPulledAt()).isEqualTo(before.getLastPulledAt());
        assertThat(service.myAccounts(lara)).singleElement()
                .satisfies(a -> assertThat(a.status()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION));
        assertThat(auditCount("SOCIAL_ACCOUNT_NEEDS_RECONNECTION", id)).isEqualTo(1);
    }

    @Test
    void aSuccessfulRefreshUpdatesTheFiguresAndOverwritesThePosts() {
        long lara = newCreator();
        String externalId = newExternalId();
        instagramAccepts("code-1", "token-1", externalId);
        long id = service.connectInstagram(lara, "code-1").id();

        // 720 interactions in 2 posts, now with 15,000 followers: 2.40 %.
        given(instagram.fetchSnapshot("token-1")).willReturn(snapshot(externalId, 15000));

        assertThat(service.refresh(id)).isTrue();

        SocialAccount after = accounts.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
        assertThat(after.getFollowers()).isEqualTo(15000);
        assertThat(after.getEngagementRate()).isEqualByComparingTo("2.40");
        assertThat(postMetrics.findBySocialAccountId(id)).hasSize(2);
    }

    // ---- Disconnect and reconnect (FR-F19.16) ----

    @Test
    void disconnectingForgetsTheTokenButKeepsTheFigures() {
        long lara = newCreator();
        instagramAccepts("code-1", "token-1", newExternalId());
        long id = service.connectInstagram(lara, "code-1").id();

        service.disconnect(lara, id);

        SocialAccount after = accounts.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION);
        assertThat(after.getFollowers()).isEqualTo(12000);
        assertThat(tokens.find(id)).isEmpty();
        assertThat(auditCount("SOCIAL_ACCOUNT_DISCONNECTED", id)).isEqualTo(1);

        // With no token, a refresh can't pull anything.
        assertThat(service.refresh(id)).isFalse();
    }

    @Test
    void connectingTheSameAccountAgainReconnectsIt() {
        long lara = newCreator();
        String externalId = newExternalId();
        instagramAccepts("code-1", "token-1", externalId);
        long id = service.connectInstagram(lara, "code-1").id();
        service.disconnect(lara, id);

        instagramAccepts("code-2", "token-2", externalId);
        SocialAccountResponse again = service.connectInstagram(lara, "code-2");

        assertThat(again.id()).isEqualTo(id);
        assertThat(again.status()).isEqualTo(SocialAccountStatus.CONNECTED);
        assertThat(accounts.findByCreatorId(lara)).hasSize(1);
        assertThat(cipher.decrypt(tokens.find(id).orElseThrow())).isEqualTo("token-2");
        assertThat(auditCount("SOCIAL_ACCOUNT_RECONNECTED", id)).isEqualTo(1);
    }

    // ---- Ownership (NF-1) ----

    @Test
    void anAccountConnectedByAnotherCreatorIsRefused() {
        long lara = newCreator();
        String externalId = newExternalId();
        instagramAccepts("code-1", "token-1", externalId);
        long id = service.connectInstagram(lara, "code-1").id();

        long nour = newCreator();
        instagramAccepts("code-n", "token-n", externalId);

        assertThatThrownBy(() -> service.connectInstagram(nour, "code-n"))
                .isInstanceOf(SocialAccountTakenException.class);
        assertThat(accounts.findByCreatorId(nour)).isEmpty();
        assertThat(cipher.decrypt(tokens.find(id).orElseThrow())).isEqualTo("token-1");
    }

    @Test
    void sheCannotDisconnectSomeoneElsesAccount() {
        long lara = newCreator();
        instagramAccepts("code-1", "token-1", newExternalId());
        long id = service.connectInstagram(lara, "code-1").id();
        long nour = newCreator();

        assertThatThrownBy(() -> service.disconnect(nour, id)).isInstanceOf(SocialAccountNotFoundException.class);
        assertThat(accounts.findById(id).orElseThrow().getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
    }

    // ---- Set-up checks ----

    @Test
    void theAuthorizationUrlComesFromInstagram() {
        long lara = newCreator();
        given(instagram.authorizationUrl("abc")).willReturn("https://instagram.test/authorize?state=abc");

        assertThat(service.instagramAuthorizationUrl(lara, "abc")).isEqualTo("https://instagram.test/authorize?state=abc");
    }

    @Test
    void connectingIsRefusedUntilInstagramIsSetUp() {
        long lara = newCreator();
        given(instagram.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> service.instagramAuthorizationUrl(lara, "abc"))
                .isInstanceOf(SocialConnectionUnavailableException.class);
        assertThatThrownBy(() -> service.connectInstagram(lara, "code"))
                .isInstanceOf(SocialConnectionUnavailableException.class);
        then(instagram).should(never()).exchangeCode(any());
    }

    @Test
    void aCreatorWithoutAProfileCannotConnect() {
        long noProfile = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}x", Role.CREATOR)).getId();

        assertThatThrownBy(() -> service.instagramAuthorizationUrl(noProfile, "abc"))
                .isInstanceOf(CreatorProfileMissingException.class);
    }

    // ---- Helpers ----

    private long newCreator() {
        String email = UUID.randomUUID() + "@test.local";
        long id = users.save(new AppUser(email, "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        return id;
    }

    private static String newExternalId() {
        return "ig-" + UUID.randomUUID();
    }

    /** Instagram accepts this code, and the token shows an account with 12,000 followers. */
    private void instagramAccepts(String code, String token, String externalId) {
        given(instagram.exchangeCode(code)).willReturn(token);
        given(instagram.fetchSnapshot(token)).willReturn(snapshot(externalId, 12000));
    }

    /** Two posts with 480 and 240 interactions: 360 per post, so 3.00 % of 12,000 followers. */
    private static InstagramClient.Snapshot snapshot(String externalId, long followers) {
        return new InstagramClient.Snapshot(externalId, "lara.eats", followers, List.of(
                new InstagramClient.PostFigures("post-1", 5000, 400, 50, 30),
                new InstagramClient.PostFigures("post-2", 3000, 200, 30, 10)));
    }

    private int auditCount(String action, long accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND entity_type = 'social_account' AND entity_id = ?
                """, Integer.class, action, accountId);
        return count == null ? 0 : count;
    }
}
