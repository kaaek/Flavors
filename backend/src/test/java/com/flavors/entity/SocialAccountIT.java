package com.flavors.entity;
 
import static org.assertj.core.api.Assertions.assertThat;
 
import com.flavors.TestcontainersConfiguration;
import com.flavors.enums.Role;
import com.flavors.enums.SocialAccountStatus;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.SocialAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
 
/**
 * Checks that the entity matches the V2 table. The rows are inserted with SQL (the way the
 * daily pull will leave them), then read back through the entity.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SocialAccountIT {
 
    @Autowired
    AppUserRepository users;
 
    @Autowired
    CreatorProfileRepository profiles;
 
    @Autowired
    SocialAccountRepository socialAccounts;
 
    @Autowired
    JdbcTemplate jdbc;
 
    private long newCreator() {
        String email = UUID.randomUUID() + "@test.local";
        long id = users.save(new AppUser(email, "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        return id;
    }
 
    @Test
    void readsAConnectedAccountWithItsNumbers() {
        long creator = newCreator();
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 12000, 4.25, now())
                """, creator, UUID.randomUUID().toString());
 
        List<SocialAccount> accounts = socialAccounts.findByCreatorId(creator);
 
        assertThat(accounts).hasSize(1);
        SocialAccount account = accounts.get(0);
        assertThat(account.getPlatform()).isEqualTo("instagram");
        assertThat(account.getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
        assertThat(account.getFollowers()).isEqualTo(12000);
        assertThat(account.getEngagementRate()).isEqualByComparingTo("4.25");
        assertThat(account.getLastPulledAt()).isNotNull();
    }
 
    // FR-F1.9: after a failed pull the last figures stay, with their old time, and the account
    // is flagged NEEDS_RECONNECTION.
    @Test
    void needsReconnectionKeepsTheLastFiguresAndTheOldTime() {
        long creator = newCreator();
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, status, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 'NEEDS_RECONNECTION', 9000, 3.10, now() - interval '2 days')
                """, creator, UUID.randomUUID().toString());
 
        SocialAccount account = socialAccounts.findByCreatorId(creator).get(0);
 
        assertThat(account.getStatus()).isEqualTo(SocialAccountStatus.NEEDS_RECONNECTION);
        assertThat(account.getFollowers()).isEqualTo(9000);
        assertThat(account.getLastPulledAt()).isBefore(Instant.now().minus(1, ChronoUnit.DAYS));
    }
 
    @Test
    void creatorWithNoAccountsGetsAnEmptyList() {
        assertThat(socialAccounts.findByCreatorId(newCreator())).isEmpty();
    }
}