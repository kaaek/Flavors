package com.flavors.platform.inbox;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes duplicates from at-least-once delivery. A consumer wraps its work in {@link #runOnce}: the message
 * id is recorded in {@code inbox} in the same transaction as the work, so the work happens exactly once
 * even if the broker redelivers, and a failed handler leaves no inbox row, so the retry runs it.
 */
public class InboxService {

    private final JdbcTemplate jdbc;

    public InboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param consumer  the handler's name (any stable string, e.g. its queue name)
     * @param messageId the envelope's {@code messageId}
     * @param type      the envelope's type, kept for debugging; may be null
     * @param work      what to do with the message; runs in this transaction
     * @return true if the work ran, false if this consumer already handled the message
     */
    @Transactional
    public boolean runOnce(String consumer, UUID messageId, String type, Runnable work) {
        int inserted = jdbc.update("""
                INSERT INTO inbox (consumer, message_id, type) VALUES (?, ?, ?)
                ON CONFLICT (consumer, message_id) DO NOTHING
                """, consumer, messageId, type);
        if (inserted == 0) {
            return false;
        }
        work.run();
        return true;
    }
}
