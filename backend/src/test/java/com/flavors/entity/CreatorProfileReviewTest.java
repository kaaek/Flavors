package com.flavors.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.enums.VerificationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CreatorProfileReviewTest {

    private static CreatorProfile pending() {
        return new CreatorProfile(7L, "Lara", "Street food", "Beirut", 10_000, 30_000);
    }

    @Test
    void aNewProfileIsPendingWithNoStorefront() {
        CreatorProfile profile = pending();

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);
        assertThat(profile.getStorefrontSlug()).isNull();
    }

    @Test
    void approvingStoresTheAdminTheTimeAndTheStorefront() {
        CreatorProfile profile = pending();
        Instant now = Instant.parse("2026-10-31T12:00:00Z");

        profile.approve(1L, "lara-7", now);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(profile.getReviewedBy()).isEqualTo(1L);
        assertThat(profile.getReviewedAt()).isEqualTo(now);
        assertThat(profile.getStorefrontSlug()).isEqualTo("lara-7");
    }

    @Test
    void rejectingNeedsAReason() {
        CreatorProfile profile = pending();

        assertThatThrownBy(() -> profile.reject(1L, " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING_APPROVAL);

        profile.reject(1L, "Account is private.", Instant.now());

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profile.getRejectionReason()).isEqualTo("Account is private.");
        assertThat(profile.getStorefrontSlug()).isNull();
    }
}
