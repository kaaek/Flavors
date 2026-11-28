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
