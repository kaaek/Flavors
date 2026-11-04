package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.flavors.enums.SocialAccountStatus;
import com.flavors.repository.SocialAccountRepository;
import com.flavors.service.InstagramClient;
import com.flavors.service.SocialAccountService;
import com.flavors.util.TokenCipher;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Which accounts the daily job pulls and how it reacts to failures; the pull itself is tested elsewhere. */
class SocialRefreshJobTest {

    private final SocialAccountService service = mock(SocialAccountService.class);
    private final SocialAccountRepository accounts = mock(SocialAccountRepository.class);
    private final InstagramClient instagram = mock(InstagramClient.class);
    private final TokenCipher cipher = TokenCipher.fromBase64Key("P5YHKVMQexj67PaCeSKT4mhXMV6LG5vCIPb7QdnH2eI=");
    private SocialRefreshJob job;

    @BeforeEach
    void setUp() {
        given(instagram.isConfigured()).willReturn(true);
        job = new SocialRefreshJob(service, accounts, instagram, cipher, mock(DataSource.class));
    }

    @Test
    void pullsEveryConnectedAccountAndCountsTheOutcomes() {
        given(accounts.findIdsByStatus(SocialAccountStatus.CONNECTED)).willReturn(List.of(1L, 2L, 3L));
        given(service.refresh(1L)).willReturn(true);
        given(service.refresh(2L)).willReturn(false);
        given(service.refresh(3L)).willReturn(true);

        assertThat(job.runOnce()).isEqualTo(new SocialRefreshJob.Result(false, 2, 1, 0));
    }

    @Test
    void anUnexpectedFailureDoesNotStopTheOtherAccounts() {
        given(accounts.findIdsByStatus(SocialAccountStatus.CONNECTED)).willReturn(List.of(1L, 2L));
        given(service.refresh(1L)).willThrow(new IllegalStateException("boom"));
        given(service.refresh(2L)).willReturn(true);

        assertThat(job.runOnce()).isEqualTo(new SocialRefreshJob.Result(false, 1, 0, 1));
        then(service).should().refresh(2L);
    }

    @Test
    void doesNothingWhereInstagramIsNotSetUp() {
        given(instagram.isConfigured()).willReturn(false);

        assertThat(job.runOnce().skipped()).isTrue();
        then(service).should(never()).refresh(anyLong());
        then(accounts).should(never()).findIdsByStatus(SocialAccountStatus.CONNECTED);
    }

    @Test
    void doesNothingWithoutTheEncryptionKey() {
        job = new SocialRefreshJob(service, accounts, instagram, TokenCipher.fromBase64Key(""), mock(DataSource.class));

        assertThat(job.runOnce().skipped()).isTrue();
        then(service).should(never()).refresh(anyLong());
    }
}
