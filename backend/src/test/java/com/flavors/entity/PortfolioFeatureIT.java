package com.flavors.entity;
 
import static org.assertj.core.api.Assertions.assertThat;
 
import com.flavors.TestcontainersConfiguration;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.Role;
import com.flavors.repository.AppUserRepository;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.CreatorProfileRepository;
import com.flavors.repository.PortfolioFeatureRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
 
/** Checks the Campaign and PortfolioFeature entities against the V3 and V6 tables. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PortfolioFeatureIT {
 
    @Autowired
    AppUserRepository users;
 
    @Autowired
    CreatorProfileRepository profiles;
 
    @Autowired
    CampaignRepository campaigns;
 
    @Autowired
    PortfolioFeatureRepository features;
 
    @Autowired
    JdbcTemplate jdbc;
 
    private long newCreator() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.CREATOR)).getId();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
        return id;
    }
 
    private long newBrand() {
        long id = users.save(new AppUser(UUID.randomUUID() + "@test.local", "{noop}not-a-real-hash", Role.BRAND)).getId();
        jdbc.update("INSERT INTO brand (user_id, business_name, contact_person, phone) VALUES (?, 'Burger Co', 'Karim', '70000000')", id);
        return id;
    }
 
    /** Inserts a campaign that satisfies the V3 checks for a non-draft status. */
    private long newCampaign(long brand, long creator, String status) {
        return jdbc.queryForObject("""
                INSERT INTO campaign (brand_id, creator_id, name, status, price_offered_cents, agreed_price_cents,
                                      start_date, last_posting_date)
                VALUES (?, ?, 'Summer burgers', ?, 50000, 50000, current_date - 10, current_date - 3)
                RETURNING id
                """, Long.class, brand, creator, status);
    }
 
    @Test
    void readsOnlyTheCompletedCampaignsOfACreator() {
        long creator = newCreator();
        long brand = newBrand();
        long completed = newCampaign(brand, creator, "COMPLETED");
        newCampaign(brand, creator, "ACTIVE");
 
        List<Campaign> found = campaigns.findByCreatorIdAndStatus(creator, CampaignStatus.COMPLETED);
 
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(completed);
        assertThat(found.get(0).getName()).isEqualTo("Summer burgers");
        assertThat(found.get(0).getAgreedPriceCents()).isEqualTo(50000);
    }
 
    @Test
    void savesAndReadsAFeaturedCampaign() {
        long creator = newCreator();
        long campaign = newCampaign(newBrand(), creator, "COMPLETED");
 
        features.save(new PortfolioFeature(campaign, creator));
 
        List<PortfolioFeature> featured = features.findByCreatorId(creator);
        assertThat(featured).hasSize(1);
        assertThat(featured.get(0).getCampaignId()).isEqualTo(campaign);
        assertThat(featured.get(0).getCreatedAt()).isNotNull();
    }
 
    @Test
    @Transactional
    void clearingTheFeaturedListRemovesOnlyThatCreatorsRows() {
        long lara = newCreator();
        long other = newCreator();
        long brand = newBrand();
        features.save(new PortfolioFeature(newCampaign(brand, lara, "COMPLETED"), lara));
        features.save(new PortfolioFeature(newCampaign(brand, other, "COMPLETED"), other));
 
        features.deleteByCreatorId(lara);
 
        assertThat(features.findByCreatorId(lara)).isEmpty();
        assertThat(features.findByCreatorId(other)).hasSize(1);
    }
}