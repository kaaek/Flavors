package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AdminTotpTest {
    private final AdminTotp totp = new AdminTotp(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptDecryptRoundTripAndFreshIv() {
        String secret = totp.newSecret();
        String first = totp.encrypt(secret);
        String second = totp.encrypt(secret);
        assertThat(first).isNotEqualTo(second);
        assertThat(totp.decrypt(first)).isEqualTo(secret);
        assertThat(totp.decrypt(second)).isEqualTo(secret);
    }

    @Test
    void totpCodeMatchesItsStepAndInvalidCodesFail() {
        String secret = totp.newSecret();
        String code = totp.codeAt(secret, totp.currentStep());
        assertThat(totp.matchingStep(secret, code)).isGreaterThan(0);
        assertThat(totp.matchingStep(secret, "bad-code")).isEqualTo(-1);
    }
}
