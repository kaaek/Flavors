package com.flavors.config;

import com.flavors.util.TokenCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for connecting social accounts (EEC-33). The variables are listed in the README. */
@Configuration
public class SocialAccountConfig {

    /**
     * Encrypts the Instagram tokens. {@code FLAVORS_TOKEN_ENCRYPTION_KEY} may be missing, so the app starts in
     * every environment; connecting is then refused with a 503 until the key is set.
     */
    @Bean
    TokenCipher tokenCipher(@Value("${flavors.token-encryption-key:}") String key) {
        return TokenCipher.fromBase64Key(key);
    }
}
