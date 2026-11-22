package com.flavors.service.impl;

import com.flavors.entity.AppUser;
import com.flavors.enums.Role;
import com.flavors.enums.UserStatus;
import com.flavors.repository.AppUserRepository;
import com.flavors.service.AuditLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side MFA enrollment, time-step replay protection and one-time recovery codes. */
@Service
public class AdminMfaService {
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final AdminTotp totp;
    private final AuditLogger audit;
    private final SecureRandom random = new SecureRandom();

    public AdminMfaService(JdbcTemplate jdbc, AppUserRepository users, AdminTotp totp, AuditLogger audit) {
        this.jdbc = jdbc; this.users = users; this.totp = totp; this.audit = audit;
    }

    public boolean enrolled(long id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM admin_mfa WHERE user_id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /** Count every TOTP/recovery attempt across sessions; expire a failure window after 15 minutes. */
    @Transactional
    public boolean allowAttempt(long userId) {
        checkAdmin(userId);
        Integer used = jdbc.queryForObject("""
                INSERT INTO admin_mfa_attempt(user_id, attempt_count, first_attempt_at)
                VALUES (?, 1, now())
                ON CONFLICT (user_id) DO UPDATE SET
                  attempt_count = CASE
                    WHEN admin_mfa_attempt.first_attempt_at < now() - interval '15 minutes'
                      THEN 1 ELSE admin_mfa_attempt.attempt_count + 1 END,
                  first_attempt_at = CASE
                    WHEN admin_mfa_attempt.first_attempt_at < now() - interval '15 minutes'
                      THEN now() ELSE admin_mfa_attempt.first_attempt_at END
                RETURNING attempt_count
                """, Integer.class, userId);
        audit.record("ADMIN_MFA_ATTEMPT", "app_user", userId);
        return used != null && used <= 5;
    }

    @Transactional
    public void clearAttempts(long userId) {
        jdbc.update("DELETE FROM admin_mfa_attempt WHERE user_id = ?", userId);
    }

    public String createEnrollmentSecret() { return totp.newSecret(); }
    public String provisioningUri(String email, String secret) {
        // TOTP secret is Base64 in storage; authenticator URI expects Base32.
        String base32 = base32(Base64.getDecoder().decode(secret));
        String label = java.net.URLEncoder.encode("Flavors:" + email, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + base32 + "&issuer=Flavors&algorithm=SHA1&digits=6&period=30";
    }
    public String manualKey(String secret) { return base32(Base64.getDecoder().decode(secret)); }

    @Transactional
    public List<String> enroll(long id, String secret, String code) {
        checkAdmin(id);
        if (enrolled(id)) throw new IllegalStateException("MFA already enabled");
        long step = totp.matchingStep(secret, code);
        if (step < 0) throw new IllegalArgumentException("Invalid authenticator code");
        jdbc.update("INSERT INTO admin_mfa(user_id, secret_ciphertext, last_used_step) VALUES (?, ?, ?)",
                id, totp.encrypt(secret), step);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16]; random.nextBytes(bytes);
            String raw = HexFormat.of().formatHex(bytes).toUpperCase();
            String formatted = raw.substring(0, 8) + "-" + raw.substring(8, 16) + "-"
                    + raw.substring(16, 24) + "-" + raw.substring(24);
            codes.add(formatted);
            jdbc.update("INSERT INTO admin_mfa_recovery_code(user_id, code_hash) VALUES (?, ?)", id, sha256(raw));
        }
        audit.record("ADMIN_MFA_ENROLLED", "app_user", id);
        clearAttempts(id);
        return codes;
    }

    @Transactional
    public boolean verifyTotp(long id, String code) {
        checkAdmin(id);
        List<String> ciphertext = jdbc.query("SELECT secret_ciphertext FROM admin_mfa WHERE user_id = ?",
                (rs, row) -> rs.getString(1), id);
        if (ciphertext.isEmpty()) return false;
        long step = totp.matchingStep(totp.decrypt(ciphertext.getFirst()), code);
        if (step < 0) return false;
        // Atomic update avoids replay, including concurrent verification on different instances.
        int changed = jdbc.update("UPDATE admin_mfa SET last_used_step = ? WHERE user_id = ? AND last_used_step < ?",
                step, id, step);
        if (changed == 1) { audit.record("ADMIN_MFA_VERIFIED", "app_user", id); clearAttempts(id); }
        return changed == 1;
    }

    @Transactional
    public boolean verifyRecovery(long id, String code) {
        checkAdmin(id);
        if (code == null || !code.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{8}){3}")) return false;
        String normalized = code.replace("-", "").toUpperCase();
        int removed = jdbc.update("DELETE FROM admin_mfa_recovery_code WHERE user_id = ? AND code_hash = ?",
                id, sha256(normalized));
        if (removed == 1) { audit.record("ADMIN_MFA_RECOVERY_USED", "app_user", id); clearAttempts(id); }
        return removed == 1;
    }

    public AppUser checkAdmin(long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid challenge"));
        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE)
            throw new IllegalArgumentException("Invalid challenge");
        return user;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String base32(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder out = new StringBuilder(); int bits = 0, buffer = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 255); bits += 8;
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer >>> bits) & 31]); }
        }
        if (bits > 0) out.append(alphabet[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }
}
