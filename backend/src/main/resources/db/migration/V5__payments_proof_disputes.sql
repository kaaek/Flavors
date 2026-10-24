-- V5: escrow payments, delivery proof and its review, disputes, and in-app notifications.
-- Conventions as in V1 (docs/Architecture.md §4.1).
-- Never edit this file once merged; add a new migration instead.

-- ---------------------------------------------------------------------------
-- Escrow (UC-F7.1, F7.2, F8.1): the brand's payment for one campaign, held by Flavors until the
-- work is approved, released automatically (Timer C) or split by a dispute decision.
-- Card details never reach Flavors: the payment provider handles them (FR-F7.2, NF-9).
-- ---------------------------------------------------------------------------
CREATE TABLE escrow_payment (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id         bigint       NOT NULL UNIQUE REFERENCES campaign (id),
    -- PENDING:  payment started, not confirmed yet
    -- HELD:     paid and held; the campaign is Active
    -- FROZEN:   the brand rejected the work, or a cancellation after work started; a dispute is open
    -- RELEASED: the creator was paid (all of it, or their share after a dispute)
    -- REFUNDED: everything went back to the brand
    status              varchar(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'HELD', 'RELEASED', 'FROZEN', 'REFUNDED')),
    -- What the brand pays: the campaign's agreed price.
    amount_cents        bigint       NOT NULL CHECK (amount_cents > 0),
    -- Filled in when the money moves out. The 15% Flavors fee applies to the creator's share only
    -- (FR-F7.11, FR-F8.5), so after a dispute decision with creator share S:
    --   fee = 15% of S, creator_net = S - fee, refunded = amount - S.
    fee_cents           bigint       NOT NULL DEFAULT 0 CHECK (fee_cents >= 0),
    creator_net_cents   bigint       NOT NULL DEFAULT 0 CHECK (creator_net_cents >= 0),
    refunded_cents      bigint       NOT NULL DEFAULT 0 CHECK (refunded_cents >= 0),
    -- The payment provider is chosen in SPIKE-2.
    provider            varchar(30)  NOT NULL,
    -- The provider's id for the payment. Unique, so a confirmation that arrives twice (EX-F7.1.3)
    -- can't create a second escrow.
    provider_payment_id varchar(100) UNIQUE,
    held_at             timestamptz,
    released_at         timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    -- Flavors never pays out more than it received.
    CHECK (fee_cents + creator_net_cents + refunded_cents <= amount_cents)
);

-- Every money movement with the provider, including failed attempts: the payment history
-- admins see (UC-F23.1) and what a retry is checked against.
CREATE TABLE payment_transaction (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escrow_id       bigint       NOT NULL REFERENCES escrow_payment (id),
    type            varchar(20)  NOT NULL CHECK (type IN ('CHARGE', 'RELEASE', 'REFUND')),
    status          varchar(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    amount_cents    bigint       NOT NULL CHECK (amount_cents > 0),
    -- The provider's id for this movement; null if the provider never answered.
    provider_ref    varchar(100) UNIQUE,
    -- The Idempotency-Key the request came with (see idempotency_key, V1). There is no foreign key:
    -- that table's key is (scope, key), and a row there only lives as long as the retry window needs.
    idempotency_key varchar(200),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX payment_transaction_escrow_id_idx ON payment_transaction (escrow_id);

-- ---------------------------------------------------------------------------
-- Delivery proof (UC-F5.1). One submission per round: round 1 is the first submission, rounds 2
-- and 3 follow the (at most two) correction requests. Every submitted version is kept (FR-F5.4)
-- and never lost (NF-5): nothing deletes these rows.
-- ---------------------------------------------------------------------------
CREATE TABLE proof_submission (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id  bigint      NOT NULL REFERENCES campaign (id),
    round        smallint    NOT NULL CHECK (round BETWEEN 1 AND 3),
    -- DRAFT until the creator makes the final submission for the round (FR-F5.2, F5.3).
    status       varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED')),
    submitted_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL))
);

CREATE INDEX proof_submission_campaign_id_idx ON proof_submission (campaign_id);
-- One final submission per round (FR-F5.3).
CREATE UNIQUE INDEX proof_submission_one_per_round_uq ON proof_submission (campaign_id, round)
    WHERE status = 'SUBMITTED';
-- One draft at a time per campaign.
CREATE UNIQUE INDEX proof_submission_one_draft_uq ON proof_submission (campaign_id)
    WHERE status = 'DRAFT';

-- Links to the published posts.
CREATE TABLE proof_link (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id bigint        NOT NULL REFERENCES proof_submission (id),
    url           varchar(2048) NOT NULL
);

CREATE INDEX proof_link_submission_id_idx ON proof_link (submission_id);

-- Screenshots: images up to 5 MB each (FR-F5.1). The file itself is in object storage under
-- storage_key; the limit of 5 files per submission is checked by the application.
CREATE TABLE proof_file (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id bigint       NOT NULL REFERENCES proof_submission (id),
    storage_key   varchar(500) NOT NULL UNIQUE,
    content_type  varchar(100) NOT NULL CHECK (content_type LIKE 'image/%'),
    size_bytes    bigint       NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX proof_file_submission_id_idx ON proof_file (submission_id);

-- The brand's decision on a submitted round (UC-F6.1). One decision per submission. When the brand
-- doesn't answer within Timer C the payment is released without a review row (FR-F6.5).
CREATE TABLE proof_review (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id bigint      NOT NULL UNIQUE REFERENCES proof_submission (id),
    decision      varchar(30) NOT NULL CHECK (decision IN ('APPROVED', 'CORRECTIONS_REQUESTED', 'REJECTED')),
    reason        text,
    decided_by    bigint      NOT NULL REFERENCES app_user (id),
    decided_at    timestamptz NOT NULL DEFAULT now(),
    -- A rejection needs a reason (FR-F6.4).
    CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE INDEX proof_review_decided_by_idx ON proof_review (decided_by);

-- ---------------------------------------------------------------------------
-- Disputes (UC-F8.1): opened when the brand rejects the work or a campaign is cancelled after work
-- started. The payment stays Frozen until an admin decides the creator's share (0-100%).
-- ---------------------------------------------------------------------------
CREATE TABLE dispute (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id           bigint      NOT NULL UNIQUE REFERENCES campaign (id),
    origin                varchar(20) NOT NULL CHECK (origin IN ('REJECTION', 'CANCELLATION')),
    opened_at             timestamptz NOT NULL DEFAULT now(),
    -- opened_at + 3 days: both sides may add evidence until then (FR-F8.3).
    evidence_deadline     timestamptz NOT NULL,
    status                varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DECIDED')),
    -- 0 means a full refund to the brand; 100 means everything goes to the creator (FR-F8.5).
    creator_share_percent smallint    CHECK (creator_share_percent BETWEEN 0 AND 100),
    decided_by            bigint      REFERENCES app_user (id),
    decided_at            timestamptz,
    -- A decision is final and complete: share, admin and time are all set together (FR-F8.6).
    CHECK ((status = 'DECIDED') = (creator_share_percent IS NOT NULL AND decided_by IS NOT NULL
                                   AND decided_at IS NOT NULL))
);

CREATE INDEX dispute_decided_by_idx ON dispute (decided_by);
-- For the admin's list of open disputes (FR-F8.4).
CREATE INDEX dispute_status_idx ON dispute (status);

-- Evidence from either side: text, a link, or both (FR-F8.3).
CREATE TABLE dispute_evidence (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_id   bigint        NOT NULL REFERENCES dispute (id),
    submitted_by bigint        NOT NULL REFERENCES app_user (id),
    text         text,
    link         varchar(2048),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CHECK (text IS NOT NULL OR link IS NOT NULL)
);

CREATE INDEX dispute_evidence_dispute_id_idx ON dispute_evidence (dispute_id);
CREATE INDEX dispute_evidence_submitted_by_idx ON dispute_evidence (submitted_by);

-- ---------------------------------------------------------------------------
-- In-app notifications (UC-F18.1). Emails are sent alongside but not stored here.
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES app_user (id),
    -- The campaign to open from the notification (FR-F18.3); null for events without one,
    -- e.g. a creator profile approved or rejected.
    campaign_id bigint      REFERENCES campaign (id),
    -- The event, e.g. CAMPAIGN_REQUEST or PAYMENT_RELEASED. No CHECK list: the events of FR-F18.1
    -- are defined in code, and adding one shouldn't need a migration.
    type        varchar(50) NOT NULL,
    message     text        NOT NULL,
    -- Null while unread.
    read_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- For "my notifications, newest first".
CREATE INDEX notification_user_id_created_at_idx ON notification (user_id, created_at DESC);
CREATE INDEX notification_campaign_id_idx ON notification (campaign_id);
