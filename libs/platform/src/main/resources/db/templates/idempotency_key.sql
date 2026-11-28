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
