package com.flavors.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts OAuth tokens before they are stored in {@code social_account.access_token_enc} (AES-256-GCM).
 *
 * <p>A stored value is a random 12-byte nonce followed by the encrypted token and its 16-byte tag. So the same
 * token encrypts differently every time, and any change to the stored bytes is detected when decrypting.
 *
 * <p>The key is 32 random bytes, Base64-encoded, from {@code FLAVORS_TOKEN_ENCRYPTION_KEY}
 * (make one with {@code openssl rand -base64 32}). Without a key the cipher is "not configured": the app still
 * starts, but nothing can be connected. A key that is set but malformed stops the app at startup.
 */
public final class TokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Null when no key is configured. */
    private final SecretKey key;

    private TokenCipher(SecretKey key) {
        this.key = key;
    }

    /** @param base64Key 32 bytes in Base64; null or blank means "not configured" */
    public static TokenCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return new TokenCipher(null);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The token encryption key is not valid Base64.");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("The token encryption key must be 32 bytes (openssl rand -base64 32).");
        }
        return new TokenCipher(new SecretKeySpec(bytes, "AES"));
    }

    public boolean isConfigured() {
        return key != null;
    }

    public byte[] encrypt(String token) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the token.", e);
        }
    }

    /** @throws IllegalStateException when the key is wrong or the stored bytes were changed */
    public String decrypt(byte[] stored) {
        requireKey();
        if (stored == null || stored.length <= NONCE_BYTES) {
            throw new IllegalStateException("There is no encrypted token to read.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            byte[] plain = cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt the token (wrong key or changed data).", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("FLAVORS_TOKEN_ENCRYPTION_KEY is not set.");
        }
    }
}
