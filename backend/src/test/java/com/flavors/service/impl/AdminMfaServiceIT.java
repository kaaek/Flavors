package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.repository.AppUserRepository;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "flavors.admin-mfa.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class AdminMfaServiceIT {
    @Autowired AdminMfaService mfa;
    @Autowired AdminTotp totp;
    @Autowired AppUserRepository users;

    @Test
    void enrollmentTotpReplayAndSingleUseRecovery() {
        AppUser admin = users.save(new AppUser(UUID.randomUUID() + "@example.com", "test-hash", Role.ADMIN));
        String secret = mfa.createEnrollmentSecret();
        String code = totp.codeAt(secret, totp.currentStep());
        List<String> recovery = mfa.enroll(admin.getId(), secret, code);
        assertThat(mfa.enrolled(admin.getId())).isTrue();
        assertThat(recovery).hasSize(10);
        // Enrollment must consume its initial time-step code.
        assertThat(mfa.verifyTotp(admin.getId(), code)).isFalse();
        assertThat(mfa.verifyRecovery(admin.getId(), recovery.getFirst())).isTrue();
        assertThat(mfa.verifyRecovery(admin.getId(), recovery.getFirst())).isFalse();
    }

    @Test
    void accountWideMfaAttemptsAreBounded() {
        AppUser admin = users.save(new AppUser(UUID.randomUUID() + "@example.com", "test-hash", Role.ADMIN));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(mfa.allowAttempt(admin.getId())).isTrue();
        }
        assertThat(mfa.allowAttempt(admin.getId())).isFalse();
    }
}
