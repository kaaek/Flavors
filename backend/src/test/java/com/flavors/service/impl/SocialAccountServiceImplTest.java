package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.service.InstagramClient.PostFigures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The engagement rate shown on the portfolio: average (likes + comments + shares) per post ÷ followers. */
class SocialAccountServiceImplTest {

    @Test
    void isTheAverageInteractionsPerPostAsAPercentageOfFollowers() {
        // (480 + 240) / 2 posts = 360 per post; 360 / 12,000 = 3 %.
        assertThat(SocialAccountServiceImpl.engagementRate(12000, List.of(post(400, 50, 30), post(200, 30, 10))))
                .isEqualByComparingTo("3.00");
    }

    @Test
    void viewsDoNotCount() {
        PostFigures manyViews = new PostFigures("p", 1_000_000, 10, 0, 0);
        assertThat(SocialAccountServiceImpl.engagementRate(1000, List.of(manyViews))).isEqualByComparingTo("1.00");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(SocialAccountServiceImpl.engagementRate(3, List.of(post(1, 0, 0)))).isEqualByComparingTo("33.33");
        assertThat(SocialAccountServiceImpl.engagementRate(3, List.of(post(2, 0, 0)))).isEqualByComparingTo("66.67");
    }

    @Test
    void isUnknownWithoutFollowersOrPosts() {
        assertThat(SocialAccountServiceImpl.engagementRate(0, List.of(post(5, 1, 1)))).isNull();
        assertThat(SocialAccountServiceImpl.engagementRate(1000, List.of())).isNull();
    }

    @Test
    void isNeverLargerThanTheColumnCanHold() {
        assertThat(SocialAccountServiceImpl.engagementRate(1, List.of(post(10_000_000, 0, 0))))
                .isEqualByComparingTo("99999.99");
    }

    private static PostFigures post(long likes, long comments, long shares) {
        return new PostFigures("p", 999, likes, comments, shares);
    }
}
