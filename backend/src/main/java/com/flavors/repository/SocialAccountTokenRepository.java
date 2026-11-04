package com.flavors.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The only way to the {@code social_account.access_token_enc} column (EEC-33). SocialAccount doesn't map
 * the column, so nothing that loads an account (portfolio, admin review) can read the token by accident.
 *
 * <p>The bytes are already encrypted by TokenCipher. Plain SQL, like IdempotencyServiceImpl: the statements
 * join the caller's transaction, and the account row must already exist (save it with saveAndFlush first).
 */
@Repository
public class SocialAccountTokenRepository {

    private final JdbcTemplate jdbc;

    public SocialAccountTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void store(long accountId, byte[] encryptedToken) {
        jdbc.update("UPDATE social_account SET access_token_enc = ? WHERE id = ?", encryptedToken, accountId);
    }

    /** A disconnected account keeps no token (V2 column comment). */
    public void clear(long accountId) {
        jdbc.update("UPDATE social_account SET access_token_enc = NULL WHERE id = ?", accountId);
    }

    /** Empty when the account doesn't exist or has no token (it was disconnected). */
    public Optional<byte[]> find(long accountId) {
        List<byte[]> rows = jdbc.query("SELECT access_token_enc FROM social_account WHERE id = ?",
                (rs, rowNum) -> rs.getBytes(1), accountId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }
}
