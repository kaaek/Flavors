-- V1: foundation tables used by every feature: users, password reset tokens, the append-only
-- audit log (NF-7), idempotency keys (NF-6) and Spring Session's tables.
-- Conventions (docs/Architecture.md §4.1): identity bigint PKs, timestamptz in UTC, varchar + CHECK
-- for statuses, indexed foreign keys, no ON DELETE CASCADE on business data.
-- Never edit this file once merged; add a new migration instead.

-- ---------------------------------------------------------------------------
-- Users: one row per creator, brand or admin account. Profile details live in
-- per-role tables (V2).
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('CREATOR', 'BRAND', 'ADMIN')),
    -- Suspended users can't log in (FR-F19.12, FR-F21.2).
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    -- Set by the application (JPA @UpdateTimestamp) on every change.
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- Emails are unique ignoring case: Bob@x.com and bob@x.com are the same account.
-- A unique index on lower(email) enforces it while keeping the email as typed.
CREATE UNIQUE INDEX app_user_email_lower_uq ON app_user (lower(email));

-- ---------------------------------------------------------------------------
-- Password reset links (UC-F19.3). Only a hash of the token is stored, so someone
-- who reads the database can't use it to reset a password.
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_token (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES app_user (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX password_reset_token_user_id_idx ON password_reset_token (user_id);

-- ---------------------------------------------------------------------------
-- Audit log (NF-7): "every important action stores user, date and time; these
-- records cannot be edited or deleted". Written only through AuditLogger.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Taken from the database clock, so no application server's clock can change it.
    occurred_at   timestamptz  NOT NULL DEFAULT now(),
    -- Null for actions done by the system (scheduled jobs, the admin seeder).
    actor_user_id bigint       REFERENCES app_user (id),
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

-- ---------------------------------------------------------------------------
-- Idempotency keys (NF-6): one row per (scope, key), used by IdempotencyService so a
-- retried payment or decision runs only once. See docs/Architecture.md §7.
-- ---------------------------------------------------------------------------
CREATE TABLE idempotency_key (
    -- What the key belongs to, e.g. 'pay' or 'webhook:<provider>'.
    scope        varchar(100) NOT NULL,
    key          varchar(200) NOT NULL,
    -- Hash of the request; the same key reused for a different request is rejected.
    request_hash varchar(64)  NOT NULL,
    status       varchar(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    -- The stored result, returned as-is to a retry of a completed request.
    response     jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    completed_at timestamptz,
    -- The primary key is what INSERT ... ON CONFLICT DO NOTHING relies on, so two concurrent
    -- requests with the same key can't both create a row.
    PRIMARY KEY (scope, key),
    -- completed_at is set exactly when the request has completed.
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- Spring Session JDBC: server-side login sessions, kept in Postgres so users stay
-- logged in across redeploys. Copied unchanged from spring-session-jdbc 4.1.1
-- (org/springframework/session/jdbc/schema-postgresql.sql). Flyway owns these tables,
-- so Spring's own schema initializer stays off (spring.session.jdbc.initialize-schema: never).
-- The ON DELETE CASCADE is Spring's: session attributes are not business data.
-- ---------------------------------------------------------------------------
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
