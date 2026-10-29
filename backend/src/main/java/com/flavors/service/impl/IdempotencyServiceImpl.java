package com.flavors.service.impl;

import com.flavors.exception.IdempotencyConflictException;
import com.flavors.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * State lives in {@code idempotency_key} (V1). Design: docs/Architecture.md §7.
 *
 * <p>Plain SQL, not JPA: the flow depends on {@code ON CONFLICT} and {@code FOR UPDATE}, and the
 * row is never edited by application code other than here.
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public IdempotencyServiceImpl(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private record Row(String requestHash, String status, String response) {
    }

    @Override
    @Transactional
    public <T> T execute(String scope, String key, String requestFingerprint, Supplier<T> action, Class<T> resultType) {
        String hash = sha256(requestFingerprint);

        // Claim the key. If another request already holds it, this inserts nothing and (while that
        // request is still open) waits for it to commit or roll back first.
        jdbc.update("""
                INSERT INTO idempotency_key (scope, key, request_hash, status)
                VALUES (?, ?, ?, 'IN_PROGRESS')
                ON CONFLICT (scope, key) DO NOTHING
                """, scope, key, hash);

        // Lock the row so concurrent retries queue up here, one at a time.
        Row row = jdbc.queryForObject("""
                SELECT request_hash, status, response::text FROM idempotency_key
                WHERE scope = ? AND key = ? FOR UPDATE
                """, (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3)), scope, key);

        if (!row.requestHash().equals(hash)) {
            throw new IdempotencyConflictException(scope, key);
        }
        if ("COMPLETED".equals(row.status())) {
            return row.response() == null ? null : json.readValue(row.response(), resultType);
        }

        T result = action.get();
        jdbc.update("""
                UPDATE idempotency_key SET status = 'COMPLETED', response = ?::jsonb, completed_at = now()
                WHERE scope = ? AND key = ?
                """, result == null ? null : json.writeValueAsString(result), scope, key);
        return result;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is required on every Java platform
        }
    }
}
