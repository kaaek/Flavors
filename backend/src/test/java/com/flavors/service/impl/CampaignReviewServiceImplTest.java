
package com.flavors.service.impl;

import com.flavors.entity.Campaign;
import com.flavors.entity.Review;
import com.flavors.enums.CampaignStatus;
import com.flavors.exception.CampaignNotFoundException;
import com.flavors.exception.ReviewNotAllowedException;
import com.flavors.exception.ValidationFailedException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ReviewRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampaignReviewServiceImplTest {

    private CampaignRepository campaigns;
    private ReviewRepository reviews;
    private CampaignReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        reviews = mock(ReviewRepository.class);
        service = new CampaignReviewServiceImpl(campaigns, reviews);
    }

    @Test
    void completedCampaignCanBeReviewed() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.COMPLETED);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));
        when(reviews.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Review result = service.submit(1L, 10L, (short) 4, "Great work");

        assertThat(result.getRating()).isEqualTo((short) 4);
        assertThat(result.getText()).isEqualTo("Great work");
        assertThat(result.getCampaignId()).isEqualTo(10L);

        verify(reviews).saveAndFlush(any(Review.class));
    }

    @Test
    void activeCampaignCannotBeReviewed() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.ACTIVE);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                service.submit(1L, 10L, (short) 4, "Good"))
                .isInstanceOf(ReviewNotAllowedException.class);

        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void duplicateReviewIsRejected() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.COMPLETED);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));
        when(reviews.existsByCampaignId(10L)).thenReturn(true);

        assertThatThrownBy(() ->
                service.submit(1L, 10L, (short) 5, "Excellent"))
                .isInstanceOf(ReviewNotAllowedException.class);

        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void anotherBrandCannotReviewCampaign() {
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.submit(2L, 10L, (short) 5, "Excellent"))
                .isInstanceOf(CampaignNotFoundException.class);

        verifyNoInteractions(reviews);
    }

    @Test
    void ratingBelowOneIsRejected() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.COMPLETED);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                service.submit(1L, 10L, (short) 0, null))
                .isInstanceOf(ValidationFailedException.class);

        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void ratingAboveFiveIsRejected() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.COMPLETED);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                service.submit(1L, 10L, (short) 6, null))
                .isInstanceOf(ValidationFailedException.class);

        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void reviewTextCannotExceedLimit() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getStatus()).thenReturn(CampaignStatus.COMPLETED);
        when(campaigns.findByIdAndBrandIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                service.submit(1L, 10L, (short) 4, "A".repeat(1001)))
                .isInstanceOf(ValidationFailedException.class);

        verify(reviews, never()).saveAndFlush(any());
    }
}
