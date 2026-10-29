package com.flavors.account;
 
import static org.assertj.core.api.Assertions.assertThat;
 
import com.flavors.TestcontainersConfiguration;
import com.flavors.common.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
 
/** Checks that the entity matches the V2 table: it saves, reloads and updates a real row. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreatorProfileIT {
 
    @Autowired
    AppUserRepository users;
 
    @Autowired
    CreatorProfileRepository profiles;
 
    private long newCreatorUser() {
        // A random email, so tests never clash with each other's rows.
        String email = UUID.randomUUID() + "@test.local";
        return users.save(new AppUser(email, "{noop}not-a-real-hash", Role.CREATOR)).getId();
    }
 
    @Test
    void savesAndReloadsAProfile() {
        long id = newCreatorUser();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
 
        CreatorProfile loaded = profiles.findById(id).orElseThrow();
 
        assertThat(loaded.getDisplayName()).isEqualTo("Lara");
        assertThat(loaded.getPriceMinCents()).isEqualTo(1000);
        assertThat(loaded.getPriceMaxCents()).isEqualTo(5000);
        assertThat(loaded.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
 
    @Test
    void savesAnEditedProfile() {
        long id = newCreatorUser();
        profiles.save(new CreatorProfile(id, "Lara", "Burgers", "Beirut", 1000, 5000));
 
        CreatorProfile lara = profiles.findById(id).orElseThrow();
        lara.updateDetails("I love burgers", "Desserts", "Sidon", 2000, 8000);
        profiles.saveAndFlush(lara);
 
        CreatorProfile reloaded = profiles.findById(id).orElseThrow();
        assertThat(reloaded.getBio()).isEqualTo("I love burgers");
        assertThat(reloaded.getFoodCategory()).isEqualTo("Desserts");
        assertThat(reloaded.getPriceMaxCents()).isEqualTo(8000);
    }
}