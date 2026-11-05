package com.flavors.entity;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
 
import com.flavors.enums.VerificationStatus;
import org.junit.jupiter.api.Test;
 
/** Plain unit tests, no database: the rules for editing portfolio details (UC-F1.2). */
class CreatorProfileTest {
 
    private CreatorProfile lara() {
        return new CreatorProfile(1L, "Lara", "Burgers", "Beirut", 1000, 5000);
    }
 
    @Test
    void validEditChangesTheDetails() {
        CreatorProfile lara = lara();
 
        lara.updateDetails("I love burgers", "Desserts", "Sidon", 2000, 8000);
 
        assertThat(lara.getBio()).isEqualTo("I love burgers");
        assertThat(lara.getFoodCategory()).isEqualTo("Desserts");
        assertThat(lara.getLocation()).isEqualTo("Sidon");
        assertThat(lara.getPriceMinCents()).isEqualTo(2000);
        assertThat(lara.getPriceMaxCents()).isEqualTo(8000);
    }
 
    // TC-F1.2.3: minimum above maximum is refused, and nothing changes.
    @Test
    void minimumAboveMaximumIsRefusedAndNothingChanges() {
        CreatorProfile lara = lara();
 
        assertThatThrownBy(() -> lara.updateDetails("new bio", "Desserts", "Sidon", 6000, 5000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum price");
 
        assertThat(lara.getBio()).isNull();
        assertThat(lara.getFoodCategory()).isEqualTo("Burgers");
        assertThat(lara.getPriceMinCents()).isEqualTo(1000);
    }
 
    @Test
    void blankCategoryOrLocationIsRefused() {
        CreatorProfile lara = lara();
 
        assertThatThrownBy(() -> lara.updateDetails(null, " ", "Beirut", 1000, 5000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lara.updateDetails(null, "Burgers", "", 1000, 5000))
                .isInstanceOf(IllegalArgumentException.class);
    }
 
    @Test
    void newProfileIsPendingApproval() {
        assertThat(lara().getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
    }
}