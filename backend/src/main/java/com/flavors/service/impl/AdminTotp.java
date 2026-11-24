package com.flavors.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RFC 6238 TOTP, with authenticated AES-GCM storage. No secret or code is logged. */
@Component
public class AdminTotp {
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;

    public AdminTotp(@Value("${flavors.admin-mfa.encryption-key:}") String key) {
        if (key.isBlank()) {
            encryptionKey = new byte[0];
            return;
        }
        try {
            encryptionKey = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Admin MFA key must be Base64-encoded", e);
        }
        if (encryptionKey.length != 32) {
            throw new IllegalStateException("Admin MFA key must decode to exactly 32 bytes");
        }
    }

    public String newSecret() {
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        return Base64.getEncoder().withoutPadding().encodeToString(secret);
    }

    private void requireKey() {
        if (encryptionKey.length != 32) throw new IllegalStateException("Configure FLAVORS_ADMIN_MFA_ENCRYPTION_KEY before admin MFA enrollment");
    }

    public String encrypt(String secret) {
        requireKey();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            ByteBuffer combined = ByteBuffer.allocate(iv.length + encrypted.length);
            combined.put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt admin MFA secret", e);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length < 29) throw new IllegalArgumentException("Invalid MFA ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, combined, 0, 12));
            return new String(cipher.doFinal(combined, 12, combined.length - 12), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt admin MFA secret", e);
        }
    }

    public long currentStep() { return clock.instant().getEpochSecond() / 30; }

    /** Returns matched step, or -1. Accepts 30 seconds of clock skew in either direction. */
    public long matchingStep(String secret, String supplied) {
        if (supplied == null || !supplied.matches("[0-9]{6}")) return -1;
        long current = currentStep();
        long match = -1;
        for (long step = current - 1; step <= current + 1; step++) {
            if (java.security.MessageDigest.isEqual(codeAt(secret, step).getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) match = step;
        }
        return match;
    }

    public String codeAt(String secret, long step) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA1"));
            byte[] hash = hmac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ((hash[offset] & 127) << 24) | ((hash[offset + 1] & 255) << 16)
                    | ((hash[offset + 2] & 255) << 8) | (hash[offset + 3] & 255);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }
}
