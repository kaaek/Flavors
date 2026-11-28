package com.flavors.client;

import com.flavors.enums.VerificationStatus;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only cross-read of {@code creator_profile} (owned by the creator service) in the shared database. */
@Component
public class JdbcCreatorDirectory implements CreatorDirectory {

    private final JdbcTemplate jdbc;

    public JdbcCreatorDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VerificationStatus> verificationStatus(long creatorId) {
        return jdbc.query("SELECT verification_status FROM creator_profile WHERE user_id = ?",
                        (rs, i) -> VerificationStatus.valueOf(rs.getString(1)), creatorId)
                .stream().findFirst();
    }
}
