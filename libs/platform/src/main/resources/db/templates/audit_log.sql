-- Platform template: the append-only audit log (NF-7). Copy this file's statements into the service's own
-- Flyway migration (V1 of a new service). Same table and triggers as the monolith's V1__foundation.sql,
-- except that actor_user_id has no foreign key: users belong to identity, so here it is a plain id.
-- Written only through com.flavors.platform.audit.AuditLogger. Never edit a migration once merged.

CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Taken from the database clock, so no application server's clock can change it.
    occurred_at   timestamptz  NOT NULL DEFAULT now(),
    -- Null for actions done by the system (scheduled jobs, event consumers, the seeder).
    actor_user_id bigint,
    -- Who acted, readable even without a join: the user's email, or 'system'.
    actor_label   varchar(254) NOT NULL,
    action        varchar(64)  NOT NULL,
    entity_type   varchar(64),
    entity_id     bigint,
    details       jsonb
);

CREATE INDEX audit_log_actor_user_id_idx ON audit_log (actor_user_id);
-- For "show the history of this campaign / payment / user".
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);

-- Append-only, enforced by the database itself: whatever the application does (JPA, plain JDBC,
-- a mistaken bulk query), changing or removing an audit row fails. This protects against the app;
-- a database owner could still drop the trigger, which is outside what NF-7 asks of the app.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

-- TRUNCATE empties the table without touching individual rows, so the row trigger above never
-- fires for it; it needs its own statement-level trigger.
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
