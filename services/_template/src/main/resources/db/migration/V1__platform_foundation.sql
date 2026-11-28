-- V1: the platform tables every service has: audit log (NF-7), idempotency keys (NF-6), outbox and inbox.
-- Copied from libs/platform/src/main/resources/db/templates/*.sql; a test keeps them in sync.
-- Service tables follow in V2, V3, ... Never edit this file once merged; add a new migration instead.

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

-- Platform template: idempotency keys (NF-6), used by com.flavors.platform.idempotency.IdempotencyService.
-- Copied from the monolith's V1__foundation.sql. One row per (scope, key) per service database.

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

-- Platform template: the transactional outbox and the inbox (processed-message table), EEC-95 §1.3.
-- Copy into the service's own Flyway migration.

-- Outbox: com.flavors.platform.outbox.OutboxWriter inserts a row in the same transaction as the business
-- change; OutboxRelay publishes unpublished rows to RabbitMQ in seq order and marks them published.
CREATE TABLE outbox (
    -- Publication order. Events of one service are relayed strictly in this order.
    seq            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The event envelope's message id: what consumers dedupe on.
    message_id     uuid         NOT NULL UNIQUE,
    type           varchar(100) NOT NULL,
    version        integer      NOT NULL CHECK (version >= 1),
    correlation_id varchar(100),
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   varchar(100) NOT NULL,
    state_version  bigint,
    occurred_at    timestamptz  NOT NULL,
    payload        jsonb        NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    -- Null until the broker confirmed the message.
    published_at   timestamptz,
    attempts       integer      NOT NULL DEFAULT 0,
    last_error     text
);

-- The relay only ever reads the unpublished tail, so keep that index tiny.
CREATE INDEX outbox_unpublished_idx ON outbox (seq) WHERE published_at IS NULL;

-- Inbox: com.flavors.platform.inbox.InboxService records each handled message in the consumer's own
-- transaction. A redelivery hits the primary key and is skipped, which makes consumers idempotent.
CREATE TABLE inbox (
    -- Which consumer (queue/handler) handled it, so two handlers in one service can both see a message.
    consumer     varchar(100) NOT NULL,
    message_id   uuid         NOT NULL,
    type         varchar(100),
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
