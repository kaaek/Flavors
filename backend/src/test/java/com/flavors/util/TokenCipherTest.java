package com.flavors.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = "P5YHKVMQexj67PaCeSKT4mhXMV6LG5vCIPb7QdnH2eI=";
    private static final String OTHER_KEY = "SUbEpC+37cnLFR5XwAfKTPzDdPblaejvcAY3244L07U=";

    private final TokenCipher cipher = TokenCipher.fromBase64Key(KEY);

    @Test
    void whatIsEncryptedCanBeDecrypted() {
        assertThat(cipher.decrypt(cipher.encrypt("IGQVJ-token"))).isEqualTo("IGQVJ-token");
    }

    @Test
    void theStoredBytesDontContainTheToken() {
        byte[] stored = cipher.encrypt("IGQVJ-token");
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("IGQVJ-token");
    }

    @Test
    void theSameTokenEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("IGQVJ-token")).isNotEqualTo(cipher.encrypt("IGQVJ-token"));
    }

    @Test
    void changedBytesAreDetected() {
        byte[] stored = cipher.encrypt("IGQVJ-token");
        stored[stored.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] stored = cipher.encrypt("IGQVJ-token");

        assertThatThrownBy(() -> TokenCipher.fromBase64Key(OTHER_KEY).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAKeyNothingCanBeEncrypted() {
        for (String missing : new String[] {null, "", "   "}) {
            TokenCipher notConfigured = TokenCipher.fromBase64Key(missing);
            assertThat(notConfigured.isConfigured()).isFalse();
            assertThatThrownBy(() -> notConfigured.encrypt("x")).isInstanceOf(IllegalStateException.class);
        }
        assertThat(cipher.isConfigured()).isTrue();
    }

    @Test
    void aMalformedKeyIsRefused() {
        assertThatThrownBy(() -> TokenCipher.fromBase64Key("not base64!!"))
                .isInstanceOf(IllegalArgumentException.class);
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> TokenCipher.fromBase64Key(sixteenBytes))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
