package com.flavors.service.impl;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.PortfolioResponse;
import com.flavors.dto.UpdatePortfolioRequest;
import com.flavors.entity.AppUser;
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.Role;
import com.flavors.exception.InvalidPortfolioException;
import com.flavors.exception.PortfolioNotAvailableException;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.service.PortfolioService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
 
/** The Manage Portfolio rules (UC-F1.2), run against the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PortfolioServiceImplIT {
 
    @Autowired
    PortfolioService service;
 
    @Autowired
    AppUserRepository users;
 
    @Autowired
    CreatorProfileRepository profiles;
 
    @Autowired
    JdbcTemplate jdbc;
 
    private long newCreator(boolean approved) {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        if (approved) {
            jdbc.update("UPDATE creator_profile SET verification_status = 'APPROVED' WHERE user_id = ?", id);
        }
        return id;
    }
 
    private long newBrand() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.BRAND)).getId();
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Burger Co', 'Karim', '70000000')", id);
        return id;
    }
 
    private long newCampaign(long brand, long creator, String status) {
        return jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                                      start_date, last_posting_date)
                VALUES (?, ?, 'Summer burgers', ?, 50000, 50000, current_date - 10, current_date - 3)
                RETURNING id
                """, Long.class, brand, creator, status);
    }
 
    private UpdatePortfolioRequest edit(Long min, Long max, List<Long> featured) {
        return new UpdatePortfolioRequest("I love burgers", "Desserts", "Sidon", min, max, featured);
    }
 
    @Test
    void showsProfileAccountsAndCompletedCampaigns() {
        long lara = newCreator(true);
        long brand = newBrand();
        long completed = newCampaign(brand, lara, "COMPLETED");
        newCampaign(brand, lara, "ACTIVE");
        jdbc.update("""
                INSERT INTO social_account (creator_id, platform, external_id, followers, engagement_rate, last_pulled_at)
                VALUES (?, 'instagram', ?, 12000, 4.25, now())
                """, lara, UUID.randomUUID().toString());
 
        PortfolioResponse portfolio = service.getMyPortfolio(lara);
 
        assertThat(portfolio.displayName()).isEqualTo("Lara");
        assertThat(portfolio.priceMinCents()).isEqualTo(1000);
        assertThat(portfolio.socialAccounts()).hasSize(1);
        assertThat(portfolio.socialAccounts().get(0).followers()).isEqualTo(12000);
        // Only the COMPLETED campaign is offered for featuring, and it starts not featured.
        assertThat(portfolio.completedCampaigns()).hasSize(1);
        assertThat(portfolio.completedCampaigns().get(0).id()).isEqualTo(completed);
        assertThat(portfolio.completedCampaigns().get(0).featured()).isFalse();
    }
 
    // TC-F1.2.1
    @Test
    void savesEditedDetailsAndFeaturedCampaign() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
 
        PortfolioResponse saved = service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(completed)));
 
        assertThat(saved.bio()).isEqualTo("I love burgers");
        assertThat(saved.foodCategory()).isEqualTo("Desserts");
        assertThat(saved.priceMaxCents()).isEqualTo(8000);
        assertThat(saved.completedCampaigns().get(0).featured()).isTrue();
 
        // And it is really stored, not only returned.
        PortfolioResponse reloaded = service.getMyPortfolio(lara);
        assertThat(reloaded.bio()).isEqualTo("I love burgers");
        assertThat(reloaded.completedCampaigns().get(0).featured()).isTrue();
    }
 
    @Test
    void unfeaturingRemovesTheCampaign() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
        service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(completed)));
 
        PortfolioResponse saved = service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of()));
 
        assertThat(saved.completedCampaigns().get(0).featured()).isFalse();
    }
 
    @Test
    void savingTheSameFeaturedCampaignTwiceKeepsOneRow() {
        long lara = newCreator(true);
        long completed = newCampaign(newBrand(), lara, "COMPLETED");
 
        service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(completed)));
        PortfolioResponse again = service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(completed, completed)));
 
        assertThat(again.completedCampaigns()).hasSize(1);
        assertThat(again.completedCampaigns().get(0).featured()).isTrue();
    }
 
    // TC-F1.2.2
    @Test
    void refusesToFeatureACampaignThatIsNotCompletedAndSavesNothing() {
        long lara = newCreator(true);
        long brand = newBrand();
        long active = newCampaign(brand, lara, "ACTIVE");
 
        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(active))))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("completed");
 
        // The valid parts of the same edit were not saved either.
        assertThat(service.getMyPortfolio(lara).bio()).isNull();
        assertThat(service.getMyPortfolio(lara).priceMaxCents()).isEqualTo(5000);
    }
 
    @Test
    void refusesToFeatureAnotherCreatorsCampaign() {
        long lara = newCreator(true);
        long other = newCreator(true);
        long othersCompleted = newCampaign(newBrand(), other, "COMPLETED");
 
        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(2000L, 8000L, List.of(othersCompleted))))
                .isInstanceOf(InvalidPortfolioException.class);
    }
 
    // TC-F1.2.3
    @Test
    void refusesMinimumAboveMaximumAndSavesNothing() {
        long lara = newCreator(true);
 
        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(9000L, 8000L, List.of())))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Minimum price");
 
        PortfolioResponse unchanged = service.getMyPortfolio(lara);
        assertThat(unchanged.bio()).isNull();
        assertThat(unchanged.priceMinCents()).isEqualTo(1000);
        assertThat(unchanged.priceMaxCents()).isEqualTo(5000);
    }
 
    @Test
    void refusesAMissingPriceRange() {
        long lara = newCreator(true);
 
        assertThatThrownBy(() -> service.updateMyPortfolio(lara, edit(null, 8000L, List.of())))
                .isInstanceOf(InvalidPortfolioException.class)
                .hasMessageContaining("Price range");
    }
 
    @Test
    void aCreatorWhoIsNotApprovedHasNoPortfolio() {
        long pending = newCreator(false);
 
        assertThatThrownBy(() -> service.getMyPortfolio(pending))
                .isInstanceOf(PortfolioNotAvailableException.class);
        assertThatThrownBy(() -> service.updateMyPortfolio(pending, edit(2000L, 8000L, List.of())))
                .isInstanceOf(PortfolioNotAvailableException.class);
    }
}