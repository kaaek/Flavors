package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.flavors.service.EscrowReleaseService;
import com.flavors.service.ProofReviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class EscrowReleaseDeadlineJobTest {
    private final ProofReviewService reviews = mock(ProofReviewService.class);
    private final EscrowReleaseService releases = mock(EscrowReleaseService.class);
    private final EscrowReleaseProcessingServiceImpl processing = new EscrowReleaseProcessingServiceImpl(reviews, releases);

    @Test
    void oneFailureDoesNotPreventLaterCandidatesAndNextRunRetries() {
        when(reviews.findOverdueCampaignIds()).thenReturn(List.of(1L, 2L, 3L));
        when(releases.authorizeOverdue(1L)).thenThrow(new IllegalStateException("interrupted")) .thenReturn(true);
        when(releases.authorizeOverdue(2L)).thenReturn(false);
        when(releases.authorizeOverdue(3L)).thenReturn(true).thenReturn(false);
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
        assertThat(processing.authorizeOverdueBatch()).isEqualTo(1);
        verify(releases, times(2)).authorizeOverdue(3L);
    }

    @Test
    void jobUsesTheBatchServiceAndConfiguredMinuteSchedule() throws Exception {
        var batch = mock(com.flavors.service.EscrowReleaseProcessingService.class);
        var job = new EscrowReleaseDeadlineJob(batch);
        job.authorizeOverdueReleases();
        verify(batch).authorizeOverdueBatch();
        Scheduled schedule = EscrowReleaseDeadlineJob.class.getMethod("authorizeOverdueReleases")
                .getAnnotation(Scheduled.class);
        assertThat(schedule.cron()).isEqualTo("${flavors.escrow.release-deadline-cron:0 * * * * *}");
        assertThat(schedule.zone()).isEqualTo("${flavors.escrow.release-deadline-zone:Asia/Beirut}");
    }
}
