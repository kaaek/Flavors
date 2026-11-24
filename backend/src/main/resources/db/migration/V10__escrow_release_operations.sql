-- EEC-45, Step 2: persistence only. No transfer, escrow transition, or dispute creation happens here.
CREATE TABLE escrow_release_operation (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- escrow_payment.campaign_id is already unique: this also permits only one full release per campaign.
    escrow_id               bigint NOT NULL UNIQUE REFERENCES escrow_payment (id),
    submission_id           bigint NOT NULL REFERENCES proof_submission (id),
    review_id               bigint REFERENCES proof_review (id),
    authorization_source    varchar(20) NOT NULL CHECK (authorization_source IN ('APPROVAL', 'TIMER_C')),
    original_amount_cents   bigint NOT NULL CHECK (original_amount_cents > 0),
    fee_cents               bigint NOT NULL CHECK (fee_cents >= 0),
    creator_net_cents       bigint NOT NULL CHECK (creator_net_cents >= 0),
    currency                varchar(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    status                  varchar(20) NOT NULL DEFAULT 'AUTHORIZED'
        CHECK (status IN ('AUTHORIZED', 'UNKNOWN', 'SUBMITTED', 'FAILED', 'CONFIRMED')),
    operation_key           varchar(100) NOT NULL UNIQUE CHECK (btrim(operation_key) <> ''),
    payout_provider         varchar(30) CHECK (btrim(payout_provider) <> ''),
    provider_ref            varchar(100) CHECK (btrim(provider_ref) <> ''),
    destination_ref         varchar(200) CHECK (btrim(destination_ref) <> ''),
    payment_transaction_id  bigint UNIQUE REFERENCES payment_transaction (id),
    authorized_at           timestamptz NOT NULL,
    submitted_at            timestamptz,
    confirmed_at            timestamptz,
    last_attempt_at         timestamptz,
    next_attempt_at         timestamptz,
    dispatch_attempts       integer NOT NULL DEFAULT 0 CHECK (dispatch_attempts >= 0),
    query_attempts          integer NOT NULL DEFAULT 0 CHECK (query_attempts >= 0),
    retryable               boolean NOT NULL DEFAULT false,
    last_error_code         varchar(100),
    lease_token             uuid,
    lease_until             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL,
    UNIQUE (payout_provider, provider_ref),
    CHECK ((authorization_source = 'APPROVAL' AND review_id IS NOT NULL)
        OR (authorization_source = 'TIMER_C' AND review_id IS NULL)),
    -- Numeric arithmetic avoids bigint overflow, including at the maximum supported amount.
    CHECK (fee_cents::numeric + creator_net_cents::numeric = original_amount_cents::numeric),
    CHECK (fee_cents::numeric = round(original_amount_cents::numeric * 15 / 100)),
    CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CHECK ((payout_provider IS NULL) = (destination_ref IS NULL)),
    CHECK (provider_ref IS NULL OR payout_provider IS NOT NULL),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK (lease_until IS NULL OR (last_attempt_at IS NOT NULL AND lease_until > last_attempt_at)),
    CHECK (status = 'FAILED' OR NOT retryable),
    CHECK (status = 'AUTHORIZED' OR
        (payout_provider IS NOT NULL AND destination_ref IS NOT NULL AND payment_transaction_id IS NOT NULL
         AND last_attempt_at IS NOT NULL AND dispatch_attempts > 0)),
    CHECK (status NOT IN ('SUBMITTED', 'CONFIRMED') OR submitted_at IS NOT NULL),
    CHECK (status <> 'CONFIRMED' OR (provider_ref IS NOT NULL AND lease_token IS NULL)),
    CHECK (submitted_at IS NULL OR submitted_at >= authorized_at),
    CHECK (confirmed_at IS NULL OR confirmed_at >= submitted_at),
    CHECK (last_attempt_at IS NULL OR last_attempt_at >= authorized_at),
    CHECK (updated_at >= authorized_at)
);

CREATE INDEX escrow_release_submission_idx ON escrow_release_operation (submission_id);
CREATE INDEX escrow_release_review_idx ON escrow_release_operation (review_id);
CREATE INDEX escrow_release_due_idx ON escrow_release_operation (status, next_attempt_at, id);

-- Persisted financial snapshots and pinned payout identity cannot be changed by a retry or raw SQL.
-- This protects the operation itself; services added in later steps own campaign eligibility,
-- provider confirmation, escrow status, audit, and idempotency under the existing campaign row lock.
CREATE FUNCTION guard_escrow_release_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'AUTHORIZED' OR NEW.payout_provider IS NOT NULL
            OR NEW.payment_transaction_id IS NOT NULL OR NEW.provider_ref IS NOT NULL
            OR NEW.submitted_at IS NOT NULL OR NEW.confirmed_at IS NOT NULL
            OR NEW.dispatch_attempts <> 0 OR NEW.query_attempts <> 0
            OR NEW.last_attempt_at IS NOT NULL OR NEW.lease_token IS NOT NULL THEN
            RAISE EXCEPTION 'A release operation must start authorized and undispatched' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF ROW(NEW.escrow_id, NEW.submission_id, NEW.review_id, NEW.authorization_source,
           NEW.original_amount_cents, NEW.fee_cents, NEW.creator_net_cents, NEW.currency,
           NEW.operation_key, NEW.authorized_at, NEW.created_at)
        IS DISTINCT FROM
       ROW(OLD.escrow_id, OLD.submission_id, OLD.review_id, OLD.authorization_source,
           OLD.original_amount_cents, OLD.fee_cents, OLD.creator_net_cents, OLD.currency,
           OLD.operation_key, OLD.authorized_at, OLD.created_at) THEN
        RAISE EXCEPTION 'Release authorization and amounts are immutable' USING ERRCODE = '23514';
    END IF;
    IF (OLD.payout_provider IS NOT NULL AND
        ROW(NEW.payout_provider, NEW.destination_ref, NEW.payment_transaction_id)
        IS DISTINCT FROM ROW(OLD.payout_provider, OLD.destination_ref, OLD.payment_transaction_id))
        OR (OLD.provider_ref IS NOT NULL AND NEW.provider_ref IS DISTINCT FROM OLD.provider_ref)
        OR (OLD.submitted_at IS NOT NULL AND NEW.submitted_at IS DISTINCT FROM OLD.submitted_at)
        OR NEW.dispatch_attempts < OLD.dispatch_attempts OR NEW.query_attempts < OLD.query_attempts
        OR NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'Payout identity, history, and counters cannot be rewritten' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'CONFIRMED' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Confirmed payout operations are immutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.status <> OLD.status AND NOT (
        (OLD.status = 'AUTHORIZED' AND NEW.status = 'UNKNOWN')
        OR (OLD.status = 'UNKNOWN' AND NEW.status IN ('SUBMITTED', 'FAILED', 'CONFIRMED'))
        OR (OLD.status = 'SUBMITTED' AND NEW.status IN ('UNKNOWN', 'FAILED', 'CONFIRMED'))
        OR (OLD.status = 'FAILED' AND NEW.status = 'UNKNOWN' AND OLD.retryable)
        -- A late, authoritative success may reconcile a previously failed transfer.
        OR (OLD.status = 'FAILED' AND NEW.status = 'CONFIRMED')
    ) THEN
        RAISE EXCEPTION 'Invalid payout state transition' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER escrow_release_operation_guard
    BEFORE INSERT OR UPDATE ON escrow_release_operation
    FOR EACH ROW EXECUTE FUNCTION guard_escrow_release_operation();
